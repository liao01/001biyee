# 手工旅行行程核心设计

## 状态

已批准设计。对应 GitHub Issue #17，父规格为 #12。本设计只覆盖手工创建、编辑和管理旅行行程；AI 修订、地图、预订参考、成员协作、费用、执行记录和公开复制分别由 #18–#24 交付，但必须复用本设计的正式事实源和命令边界。

## 目标

让已登录用户创建私有旅行行程草稿，维护有序多目的地、日期和每日安排，并通过明确的生命周期管理一次旅行。任何失败写操作不得留下部分数据，系统可以建议状态但不得未经用户确认修改正式状态。

## 决策摘要

- 后端新增 `itinerary` 深模块，遵循 [ADR 0002](../../adr/0002-modular-monolith-first.md)；模块外只能调用正式应用接口或消费领域事件，不得访问内部 Mapper、数据库对象或实现类。
- MySQL 是旅行行程正式事实的唯一来源。Redis、搜索索引、前端状态和后续 AI 输出都不能成为竞争事实源。
- 每个行程支持有顺序的多个结构化目的地；第一项为主目的地。行程保存一个正式 IANA 时区和一个 ISO 4217 基准币种。
- 每个写命令包含 UUID `commandId` 和 `expectedVersion`。命令在单个 MySQL 事务中完成，成功后行程版本加一；旧版本提交返回冲突。
- 编辑器逐操作自动保存，不提供会覆盖其他修改的整页保存。删除安排项为软删除。

## 领域模型

### 旅行行程

`itinerary` 保存：

- `id`：雪花 ID。
- `owner_member_id`：当前唯一行程负责人。
- `title`：1–100 个 Unicode 字符，去除首尾空白。
- `start_date`、`end_date`：当地日历日期，开始日期不得晚于结束日期，最多连续 366 天。
- `time_zone`：有效 IANA Zone ID，例如 `Asia/Shanghai`。
- `base_currency`：大写 ISO 4217 三字母币种代码。
- `status`：`DRAFT`、`PLANNED`、`IN_PROGRESS`、`COMPLETED`、`CANCELLED`、`ARCHIVED`。
- `version`：从 1 开始的单调递增乐观锁版本。
- `created_at`、`updated_at`：UTC 时间戳。

行程创建后保持私有。#17 中只有负责人可读取或修改；不存在与不属于当前负责人的行程对外都表现为 404。#21 必须扩展同一访问策略接口，不能在 Controller 或 Mapper 中复制角色判断。

### 目的地

`itinerary_destination` 保存：`id`、`itinerary_id`、`name`、可选的 ISO 3166-1 alpha-2 `country_code`、有效 IANA `time_zone`、从 1024 开始的 `position`、创建与更新时间。

一个行程至少有一个目的地，名称去除首尾空白后为 1–100 个字符。第一项是主目的地，只是有序集合的第一个元素，不另建可分叉的 `primary_destination_id` 事实源。整组目的地通过一个原子命令替换和排序；仍被后续正式地点引用的目的地不能静默删除。

### 行程日

`itinerary_day` 为日期范围内每个自然日保存一行：`id`、`itinerary_id`、`day_date`、创建与更新时间。`(itinerary_id, day_date)` 唯一。

创建行程及延长日期范围时自动补齐空白行程日。缩短范围时，只有被排除日期不存在未删除安排项才允许删除相应行程日；否则整个命令失败，不自动删除或移动安排。

### 安排项

`itinerary_item` 保存：

- `id`、`itinerary_id`、`itinerary_day_id`。
- `title`：1–120 个 Unicode 字符。
- `place_name`：可为空；非空时最多 200 个字符。#19 可在不替换此人工可读名称的前提下增加正式地点引用。
- `start_time`、`end_time`：均为空表示未定时间；否则必须同时存在，且同一自然日内 `end_time > start_time`。
- `notes`：可为空，最多 2000 个字符。
- `estimated_cost`：可为空；非空时为 `DECIMAL(14,2)`、大于等于 0，币种使用行程 `base_currency`。
- `position`：同一天内的有序位置，从 1024 开始留间隔；对外顺序由 `(position, id)` 唯一确定。
- `deleted_at`、`created_at`、`updated_at`。

同一天的未删除、已定时安排项不得重叠，采用半开区间 `[start_time, end_time)`，因此前一项结束时间可以等于后一项开始时间。跨午夜活动拆成相邻日期的两个安排项。未定时间安排不参与时间冲突判断，但仍参与人工排序。

删除安排项只设置 `deleted_at`，当前查询与排序排除软删除项。后续审计记录可以引用原 ID；软删除项不能重新激活，撤销删除由创建新项表达。

## 生命周期

唯一合法状态转换为：

- `DRAFT` → `PLANNED`、`CANCELLED`、`ARCHIVED`
- `PLANNED` → `DRAFT`、`IN_PROGRESS`、`CANCELLED`、`ARCHIVED`
- `IN_PROGRESS` → `COMPLETED`、`CANCELLED`
- `COMPLETED` → `ARCHIVED`
- `CANCELLED` → `ARCHIVED`
- `ARCHIVED` 无后继状态

状态转换必须由负责人显式提交命令，经过 `expectedVersion` 校验并在事务中持久化。取消表示终止，归档表示退出日常操作；需要继续已取消或已归档旅行时，后续使用复制语义创建独立草稿，不恢复旧状态。

状态建议按行程正式时区的当地日期计算，且只返回建议，不写数据库：

- 正式状态为 `DRAFT` 且安排已满足规划最低条件时，可建议 `PLANNED`。
- 当地日期早于 `start_date` 时不建议进入 `IN_PROGRESS`。
- 当地日期位于闭区间 `[start_date, end_date]` 且状态为 `PLANNED` 时，建议 `IN_PROGRESS`。
- 当地日期晚于 `end_date` 且状态为 `IN_PROGRESS` 时，建议 `COMPLETED`。
- `COMPLETED`、`CANCELLED`、`ARCHIVED` 不根据日期建议其他正式状态。

规划最低条件为：至少一个目的地、日期范围有效、至少一个未删除安排项，且所有安排项规则通过。它只影响建议和 `DRAFT → PLANNED` 校验，不阻止保存不完整草稿。

## 命令、幂等与事务

应用接口以负责人 ID、行程 ID、`commandId`、`expectedVersion` 和命令载荷为输入。创建命令没有既有行程可锁定，固定要求 `expectedVersion = 0`；其他命令要求提交读取快照中的当前版本。命令类型包括：创建行程、更新行程概要、替换目的地、创建安排项、更新安排项、删除安排项、重排行程日安排和转换状态。

`itinerary_command` 保存 `command_id`、`member_id`、`operation`、可为空的 `itinerary_id`、规范化请求摘要、结果行程 ID、结果版本和创建时间；`command_id` 全局唯一。

- 首次命令执行时，在同一事务中记录请求摘要与结果。
- 相同 `commandId`、成员、操作和请求摘要重试时返回原结果，不重复写入或增加版本。
- 相同 `commandId` 携带不同请求时返回 `IDEMPOTENCY_CONFLICT`。
- 两个并发的相同命令由 `command_id` 唯一约束收敛；唯一键冲突方在首个事务完成后重新读取命令记录，并按相同摘要返回原结果或报告冲突，不能把数据库异常直接暴露给客户端。
- 创建之外的写命令先以 `SELECT ... FOR UPDATE` 锁定负责人所属行程，再比较 `expectedVersion`；不匹配返回 `VERSION_CONFLICT`，且不留下任何子表变化。
- 重排命令必须提交当天所有未删除安排项 ID 的完整排列；缺失、重复、额外或跨行程 ID 均使整个命令失败。
- 位置值接近上限或间隔不足时，在同一事务中按当前顺序重新编号为 1024 的倍数，不改变可观察顺序。

## HTTP 契约

所有端点位于 `/web/itineraries`，使用现有 Bearer 访问令牌。写请求的 `commandId` 和 `expectedVersion` 位于 JSON 正文，避免代理或日志把它们误当凭据；访问令牌仍只位于 `Authorization` 头。

- `GET /web/itineraries?status=&cursor=&limit=`：当前负责人的行程列表，默认排除 `ARCHIVED`，使用稳定游标分页。
- `POST /web/itineraries`：创建私有草稿，提交标题、日期、正式时区、基准币种和至少一个目的地。
- `GET /web/itineraries/{itineraryId}`：返回完整编辑快照、版本、允许的状态转换和当前状态建议。
- `PATCH /web/itineraries/{itineraryId}`：更新标题、日期、时区或基准币种；缩短日期执行保护规则。
- `PUT /web/itineraries/{itineraryId}/destinations`：原子替换与排序目的地。
- `POST /web/itineraries/{itineraryId}/items`：在指定日期新增安排项。
- `PATCH /web/itineraries/{itineraryId}/items/{itemId}`：修改安排项，可移动到范围内的另一日期。
- `DELETE /web/itineraries/{itineraryId}/items/{itemId}`：软删除安排项；正文仍包含命令元数据。
- `PUT /web/itineraries/{itineraryId}/days/{dayId}/item-order`：提交该日完整未删除安排项 ID 排列。
- `POST /web/itineraries/{itineraryId}/status-transitions`：提交目标状态。

成功写响应至少返回 `itineraryId` 和新 `version`；创建安排项还返回 `itemId`。完整快照只由读取端点返回，避免每个命令复制大型聚合。

错误响应沿用 `CommonResp` 外壳，并在 `content` 中返回稳定 `errorCode`：

- 400：`INVALID_ITINERARY`、`INVALID_DESTINATION`、`INVALID_ITEM`、`TIME_CONFLICT`、`DATE_RANGE_CONTAINS_ITEMS`。
- 401：现有身份模块未认证响应。
- 404：`ITINERARY_NOT_FOUND`，同时覆盖不存在和无访问权。
- 409：`VERSION_CONFLICT`、`IDEMPOTENCY_CONFLICT`、`INVALID_STATUS_TRANSITION`。

错误消息不得包含其他成员 ID、SQL、请求摘要或私有行程内容。

## 前端体验

新增三个路由：

- `/itineraries`：显示当前负责人的进行中/近期行程和草稿，可查看归档列表。
- `/itineraries/new`：创建标题、多个目的地、日期、时区和基准币种，成功后进入编辑器。
- `/itineraries/:id`：按日期分组的行程编辑器。

前端使用独立 `itineraryHttp` 作为唯一 HTTP 适配器，编辑器状态和命令队列封装在 `itineraryEditor` 模块。每个用户动作生成新的 UUID `commandId`，使用当前快照版本串行提交：

- 提交期间显示“保存中”；成功更新本地版本并显示“已保存”。
- 普通失败恢复该命令前的局部界面状态并显示明确错误。
- `VERSION_CONFLICT` 停止后续命令，重新读取服务端快照；不自动重放可能覆盖他人修改的命令。
- 页面离开前若仍有命令在途，显示浏览器离开确认；已经失败的命令不伪装为已保存。

安排项支持鼠标拖拽排序，同时提供可聚焦的“上移/下移”按钮和正确的 ARIA 标签，确保键盘可完成同一操作。表单按服务端相同规则进行即时提示，但后端仍是正式校验事实源。

## 模块边界与后续能力

正式应用接口负责：负责人行程列表、完整快照读取、所有命令和状态建议。内部领域类型不依赖 Spring、MyBatis 或 HTTP。

- #18 的 AI 模块只能保存结构化行程修订建议；用户确认后通过本模块的批量修订命令应用，不能直接写行程表。
- #19 的地点与路线模块通过稳定的安排项/目的地读取接口消费 ID，并在本模块定义的扩展接缝关联地点引用。
- #21 通过 `ItineraryAccessPolicy` 扩展负责人、可编辑成员和只读成员权限；Controller 不增加第二套角色判断。
- #22、#23 分别关联行程、成员和安排项 ID，不能覆盖预计金额或计划项。
- #24 的复制服务调用本模块的复制应用接口创建新负责人拥有的独立聚合。

通知、搜索、统计等跨模块副作用只能使用事务提交后的领域事件与幂等消费者。#17 不预先建设未被当前行为使用的消息中间件或服务拆分。

## 数据迁移与文档

空库结构更新 `sql/travel_share.sql`；升级规则新增版本化迁移。迁移必须复用 `MigrationSpec`，支持 dry-run、apply、重复执行、部分 DDL 恢复和差异统计。由于现有库没有行程数据，迁移只新增缺失结构，不生成合成业务行程。

数据文档记录表、字段、约束、读写链路、幂等事实源和后续兼容边界。新增 ADR 记录旅行行程聚合与命令边界，防止后续 AI、协作和费用模块绕过。

## 测试与验收

- 领域单元测试覆盖状态图、日期范围、IANA 时区、币种、时间半开区间冲突、规划最低条件和状态建议，使用固定 `Clock`。
- MySQL 迁移测试覆盖 dry-run、apply、重复执行、部分 DDL 恢复、表/索引/约束差异和无旧规则残留。
- Testcontainers HTTP 集成测试使用真实 MySQL 和身份令牌，验证负责人隔离、创建后重开、日期扩缩保护、安排项增删改移、完整排序排列、软删除、幂等重试、版本冲突、事务回滚和全部合法/非法状态转换。
- 架构测试阻止模块外访问 `itinerary.infrastructure`，并阻止行程模块访问旧全局 Mapper。
- Vue 单元与集成测试覆盖创建表单、完整快照渲染、逐命令自动保存、失败回滚、冲突重载、键盘排序、状态建议只提示不自动提交。
- 运行现有 Python、Maven、前端、生产构建、秘密扫描和完整容器身份套件，确保 #13–#16 不回归。
- 浏览器验收至少覆盖：登录后创建多目的地草稿、重新打开、增加并排序两天安排、刷新保持、触发一次校验失败且无半条数据、确认一次状态转换。测试数据必须带统一批次标识并在结束前精确清理或登记。

## 非目标

- AI 生成或自动写入正式行程。
- 地图坐标、路线、酒店、火车或航班查询。
- 成员邀请、角色、共同费用或执行记录。
- 发布、复制、推荐或内容审核。
- 自动状态转换、硬删除安排项、跨午夜单项、未授权读取或平台内支付。
- 合并 PR、正式部署、生产迁移或真实用户数据写入；这些仍需各自授权和验收。

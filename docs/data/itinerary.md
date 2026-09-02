# 旅行行程核心数据链路

本文记录 #17 手工旅行行程核心的数据口径。MySQL 中的五张规范化表是行程当前状态与命令结果的唯一事实源；Controller 响应、Vue 状态、缓存、日志以及任何 JSON 投影都不是独立事实源，必须能够从这些表和正式领域规则重新构建。

## 正式事实源

- 表结构、约束与收敛检查以 [`20260901_itinerary_core.sql`](../../sql/migrations/20260901_itinerary_core.sql) 为正式升级脚本；全新环境初始化由 [`travel_share.sql`](../../sql/travel_share.sql) 提供相同表契约。
- 领域不变量和状态行为以 [`ItineraryRules.java`](../../business/src/main/java/com/jiawa/lyw/itinerary/domain/ItineraryRules.java) 为准，本文不复制完整状态枚举或迁移矩阵。
- 写入事务、幂等和版本判断以 [`DefaultItineraryApplicationService.java`](../../business/src/main/java/com/jiawa/lyw/itinerary/application/DefaultItineraryApplicationService.java) 为准；模块外不得直接调用内部 Mapper。
- 持久化端口由 [`ItineraryRepository.java`](../../business/src/main/java/com/jiawa/lyw/itinerary/application/ItineraryRepository.java) 定义，MyBatis 映射只负责参数绑定和数据装配。
- HTTP 边界以 [`ItineraryController.java`](../../business/src/main/java/com/jiawa/lyw/itinerary/api/ItineraryController.java) 为准；可执行行为契约以 [`ItineraryHttpIT.java`](../../business/src/test/java/com/jiawa/lyw/itinerary/api/ItineraryHttpIT.java) 为准。

## 五张正式表

| 表 | 业务事实 | 主要字段口径 |
| --- | --- | --- |
| `itinerary` | 旅行行程聚合根 | 负责人、标题、起止日期、IANA 时区、ISO 4217 基准币种、状态、行程版本及创建/更新时间。负责人引用正式会员；版本从 1 开始并在每次成功命令后递增。 |
| `itinerary_destination` | 有序行程目的地 | 所属行程、名称、可选国家代码、时区、位置及审计时间。按位置和 ID 稳定排序，首项是主要目的地。 |
| `itinerary_day` | 日期范围内的日程日 | 所属行程、自然日期及审计时间。同一行程同一日期唯一；创建行程及日期扩展时由应用服务生成。 |
| `itinerary_item` | 日程日中的有序行程条目 | 同时保存所属行程与日程日、标题、可选地点、成对出现的起止时间、备注、可选预计费用、位置、软删除时间及审计时间。读取只返回未软删除条目。 |
| `itinerary_command` | 行程命令的幂等保留与结果 | 命令 UUID、执行成员、操作、目标行程、期望版本、规范化请求摘要、结果行程/条目/版本和创建时间。只保存摘要与最小结果，不保存原始 JSON 命令载荷。 |

外键保证目的地、日程日和条目属于行程，条目还必须引用一个日程日。写入前，应用服务会从已锁定的聚合快照验证日程日、条目和目的地 ID 的所属关系；不能只依赖请求中的行程 ID。

## 写入、读取与事务边界

浏览器通过 `itineraryHttp.js` 调用正式 HTTP 接口；访问令牌仍由统一身份会话在内存中管理，不建立行程专用凭据存储。Controller 只转换 HTTP 模型并取得当前会员，所有业务写入都进入应用服务。

每个命令在一个 `READ_COMMITTED` 事务内完成：

1. 锁定聚合根并验证当前成员具备编辑权限；创建命令在写聚合前先保留命令 UUID。
2. 以命令 UUID、成员、操作和请求摘要判断首次执行、合法重放或幂等冲突。
3. 比较期望行程版本，验证日期、时间冲突、排序排列和状态迁移等领域规则。
4. 写入聚合数据并以旧版本为条件递增行程版本。
5. 在同一事务内补全命令结果；任何步骤失败都会回滚命令保留和业务写入。

同一命令 UUID 与相同内容重复送达时返回既有结果，不再次修改数据；同一 UUID 被不同成员、操作或内容复用时拒绝。版本冲突要求调用方重新读取完整快照，不能自动覆盖较新的修改。列表和详情只读取当前会员有权访问的行程；不存在与无权访问在 HTTP 边界均表现为未找到，避免泄露他人资源。

目的地替换是聚合内的事务性全量替换；行程条目删除使用 `deleted_at` 软删除。缩短日期范围时只允许删除范围外且没有条目的日程日；有条目的日期不会被隐式删除。`created_at` 表示首次持久化时间，`updated_at` 表示最近一次成功业务写入时间，均由应用提供的统一时钟写入或数据库默认值保护。

## 迁移、恢复与回滚

升级脚本默认 dry-run，先报告缺失表、额外表、缺失字段、索引、外键和历史字段残留；只有调用方显式设置 `@apply_itinerary_core_migration = 1` 才创建缺失表。脚本使用 `CREATE TABLE IF NOT EXISTS`，可在首次执行、重复执行和前次仅创建部分表后收敛。迁移测试会在随机前缀的隔离 MySQL schema 中验证 dry-run、重复执行、部分恢复以及全新初始化 SQL 与升级脚本的一致性，并在结束时精确删除该 schema。

该迁移只新增表，不迁移或删除历史业务数据。上线前必须完成数据库备份并保存迁移前 schema 证据；失败时先停止行程写流量并重新执行 dry-run 判断差异。若尚未产生行程数据，可以按外键逆序删除这五张表后恢复应用版本；若已经产生数据，不允许用删表作为回滚，应恢复备份到隔离库、核对行程和命令数量，再以经过评审的前向迁移修复。任何共享或生产数据库操作都需要独立授权，本文不代表已经部署。

## 扩展边界

#18–#32 的 AI 建议、协作成员、预算与费用、预订参考、地图、执行记录、公开复制、推荐和治理数据不得塞入这五张表的通用 JSON 字段。扩展模块通过正式应用接口或领域事件引用行程 ID；只有属于手工行程核心且需要与版本命令原子一致的事实，才应在后续评审后扩展本聚合。

## 2026-09-02 本地验收记录

- Python 回归 58 项通过，包含行程迁移的 dry-run、重复执行、部分恢复、基线一致性和本文/CI 契约；隔离迁移 schema 均已精确删除。
- Maven `deployment` 发布包构建通过：业务模块 82 项、生成器 1 项普通测试通过。生产 Jar 包含受版本控制的生产配置，未包含本机 `application.properties`、`application.yml`、example 配置、行程集成测试类或 Testcontainers/Docker Java 测试依赖。
- 本机回环 MySQL 模式运行 45 项 Java 集成测试，其中 44 项通过；仅 Docker 完整模式的 Redis 用例按设计跳过。行程 HTTP 3 项、仓储 13 项均通过；每项测试后清空五张表与 `IT-TEST-#17-` 账号，类结束删除隔离数据库。
- 用户端 90 项测试和生产构建通过；构建仍有既有大包体积警告，不据此声称前端性能门禁通过。应用内浏览器验证未登录路由守卫、登录提示、编辑器非空渲染、桌面与 375px 无横向溢出、键盘替代排序按钮和新增安排交互，相关控制台无错误或警告。
- 当前工作树和全部可达 Git 对象秘密扫描均为 0 项发现。该结果不替代完整供应链或攻击路径安全审计。

Docker 引擎本轮不可用，因此没有把边界替身的浏览器页面冒充为“真实浏览器 + 隔离后端”的完整端到端证据；真实 MySQL/HTTP 行为由上述 Java 集成套件证明，浏览器渲染与交互由隔离前端预览证明。Docker 恢复后仍需按实施计划补跑同一浏览器的注册/登录、创建三日行程、冲突/日期收缩、状态迁移和刷新一致性旅程，清理精确批次数据并等待远端 required checks。未执行合并或部署。

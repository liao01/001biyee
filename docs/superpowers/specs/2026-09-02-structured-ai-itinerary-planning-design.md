# 结构化 AI 行程规划闭环设计

状态：已批准。对应 GitHub Issue #18，父规格为 #12，依赖 #17 的旅行行程聚合与命令边界。用户批准使用当前服务器中的 Dify 作为首个规划适配器，并通过 Dify Workflow 接入外挂知识库；本设计不授权部署、修改线上工作流或改动 Dify 数据。

## 目标与边界

本设计让已登录的行程负责人在一条已有旅行行程中保存结构化规划需求，请求 AI 生成经过确定性业务校验的行程修订建议，预览建议与当前行程的差异，并逐项或整体确认。只有用户确认成功后，选中的建议才通过旅行行程模块的正式命令边界原子写入。

本次交付包含：

- 目的地、日期、预算、同行人数和旅行偏好的结构化规划需求及草稿保存；
- 版本化、供应商无关的 AI 输出契约；
- 通过现有自部署 Dify Workflow 使用外挂知识库生成有依据的候选建议；
- 日期、时间冲突、地点字段和预算的确定性校验；
- 建议生成、失败、拒绝、过期和确认的完整状态；
- 当前行程与建议之间可审查的结构化差异；
- 逐项选择或整体确认，并通过单个幂等行程命令原子应用；
- 可控 AI 替身、固定评测集、HTTP 契约和前端关键状态测试。

本次不包含地图地点解析、路线、酒店/交通搜索、协作成员权限、费用分摊、执行记录、公开复制或生产部署；这些分别由 #19–#24 及后续 Ticket 交付。现有通用 AI 对话页面不作为本模块的事实源，也不直接接入行程写入。

## 方案比较

### 方案 A：在现有 Controller 中直接调用模型并写行程

开发量最小，但模型输出、校验、事务和正式行程写入耦合在一起；无法可靠保存失败建议、预览差异或选择性确认，也违反“AI 不直接修改正式行程”的已批准边界，因此不采用。

### 方案 B：独立规划模块、供应商适配器和不可变修订建议（推荐）

新增 `itineraryplanning` 模块，MySQL 保存结构化规划需求、不可变建议及建议操作；模型仅通过 `ItineraryPlannerGateway` 产生供应商无关的候选文档。应用层完成契约解析和确定性校验，用户确认时调用行程模块新增的批量修订命令。

该方案以当前服务器的 Dify Workflow API 作为首个在线适配器，由 Dify 在工作流内部完成知识库检索、模型调用和结构化输出；测试使用确定性替身，未来仍可替换为 LangChain4j 直连或其他供应商。旅游平台不读取或复用 Dify 的 PostgreSQL、Redis、向量库或数据卷，只通过带独立应用 API Key 的 HTTPS 或容器网络 API 调用已发布工作流。

### 方案 C：以 Dify 工作流作为规划领域和建议存储

Dify 适合编排提示词和知识库，但若让其保存规划状态或决定正式写入，会形成 MySQL 之外的第二业务事实源，并让本地/CI 验收依赖外部工作流配置。因此本次使用 Dify 作为 `ItineraryPlannerGateway` 的首个适配器，但不把 Dify 运行记录当成规划领域状态，也不让 Dify 调用正式行程写接口。

## 模块与依赖方向

新增顶层包：

```text
itineraryplanning
├── domain          规划需求、修订建议、建议操作、状态和校验结果
├── application     用例接口、规划网关、仓储接口、差异与确认编排
├── infrastructure  MyBatis 仓储、Dify Workflow 适配器、JSON 契约解析
└── api             负责人 HTTP 接口与错误映射
```

允许的依赖方向为：

```text
HTTP → itineraryplanning.application → itineraryplanning.domain
                               ├──────→ ItineraryPlannerGateway
                               ├──────→ PlanningRepository
                               └──────→ itinerary.application.ItineraryApplicationService
```

规划模块只能通过 `ItineraryApplicationService` 读取快照和提交确认命令，不依赖行程 Mapper、表行类型或实现类。AI 适配器不能持有行程仓储或应用服务，因此模型调用路径在结构上没有正式写入能力。现有 `/web/customerService` 使用旧 AnythingLLM 风格请求且含硬编码凭据和 URL，不作为兼容入口继续保留；#18 删除该入口及其硬编码凭据，历史聊天读取如仍有产品价值则只保留为独立只读能力。

## 正式事实源与数据模型

MySQL 仍是正式业务数据唯一事实源。AI 对话原文若保留，只能进入现有 MongoDB 对话存储；它不是建议状态、确认结果或正式行程的事实源。

新增五张表：规划请求、规划目的地、修订建议、建议操作和用户决定。规划目的地紧随请求表说明：

### `itinerary_planning_request`

- `id`、`itinerary_id`、`owner_member_id`；
- `schema_version`，首版固定为 `itinerary-planning-request/v1`；
- `start_date`、`end_date`、`budget_amount`、`budget_currency`、`party_size`；
- `preferences_json`，只保存已验证的偏好枚举和补充文本，不保存提示词；
- `status`：`DRAFT`、`SUBMITTED`、`GENERATING`、`READY`、`FAILED`、`CANCELLED`；
- `version`、`created_at`、`updated_at`。

目的地使用 `itinerary_planning_destination` 按 `position` 规范化保存，字段为名称、国家码和时区。请求中的日期、币种和目的地可以与当前行程相同，但仍显式保存，确保 AI 输入可复现和可审计。

### `itinerary_revision_proposal`

- `id`、`planning_request_id`、`itinerary_id`、`owner_member_id`；
- `base_itinerary_version`，表示生成时读取的正式行程版本；
- `contract_version`，首版固定为 `itinerary-revision/v1`；
- `status`：`VALIDATING`、`READY`、`INVALID`、`FAILED`、`CONFIRMED`、`REJECTED`、`EXPIRED`；
- 非敏感生成元数据：供应商标识、模型标识、提示模板版本、请求耗时、输入/输出 token 数（供应商可提供时）、失败分类；
- `created_at`、`resolved_at`。

### `itinerary_revision_operation`

每行是一项可选择的不可变建议，包含稳定 `operation_key`、操作类型、目标日期或条目 ID、展示摘要、结构化载荷、预计费用变化和校验状态。首版允许：

- `ADD_ITEM`：为某日新增安排；
- `UPDATE_ITEM`：替换某个既有安排的完整可编辑字段；
- `DELETE_ITEM`：删除某个既有安排；
- `REORDER_DAY_ITEMS`：给出某日完整条目顺序。

首版不让 AI 修改负责人、状态、目的地、日期范围、时区或币种。这些高影响字段仍由 #17 的手工界面修改，减少选择性确认时的依赖和歧义。

操作载荷以带 `contract_version` 的 JSON 保存，但它是规划模块内部、经过解析和校验的不可变建议，不是正式行程 JSON，也不会回写核心表的扩展字段。读取时再次按版本反序列化；未知版本拒绝展示和确认。

### `itinerary_revision_resolution`

保存一次确认或拒绝决定：`id`、`proposal_id`、`member_id`、`decision_id`、决定类型、选中的操作键集合摘要、确认前期望行程版本、行程命令 ID、结果版本和时间。`decision_id` 唯一，支持网络重试幂等返回相同结果。

## AI 输入与输出契约

规划网关接收纯领域输入：当前行程快照、已提交规划需求和允许的操作类型。Dify 适配器把这些内容序列化为 `planning_request_json`、`itinerary_snapshot_json` 和 `contract_version` 三个 Workflow 输入变量，并使用稳定、不可逆的成员伪标识作为 Dify `user`。不得把 Cookie、令牌、邮箱、真实成员 ID、私人对话原文或数据库行直接发送给 Dify。

Dify Workflow 的建议拓扑为：开始节点 → 知识检索节点 → LLM/模板节点 → 输出节点。知识库可以保存目的地指南、开放时间注意事项、交通常识和旅行规则，但检索结果只作为生成依据，不直接成为正式地点或价格事实。输出节点必须只返回 `revision_json`，可选返回不含文档正文的 `knowledge_reference_ids`；后端不信任 Dify 的校验结论，仍完整执行本设计的确定性校验。

模型输出必须是单个 `itinerary-revision/v1` 文档：

```json
{
  "contractVersion": "itinerary-revision/v1",
  "summary": "三天城市文化与美食安排",
  "operations": [
    {
      "operationKey": "day-1-museum",
      "type": "ADD_ITEM",
      "date": "2026-10-02",
      "title": "参观博物馆",
      "placeName": "城市博物馆",
      "startTime": "09:30",
      "endTime": "11:30",
      "notes": "提前预约",
      "estimatedCost": 80.00
    }
  ]
}
```

解析器拒绝 Markdown 包裹、额外顶层字段、未知操作、重复 `operationKey`、超出长度/精度的值和超过最大操作数的文档。提示词只能帮助模型满足契约；后端校验器才是可信门禁。

## 确定性校验

候选文档必须在写入 `READY` 前完成以下检查：

- 契约版本、字段类型、长度、枚举、操作数量和载荷大小合法；
- 所有日期落在规划请求和当前行程的日期范围内；
- 开始/结束时间成对出现且结束时间晚于开始时间；
- 应用全部候选操作后的同一天有时间安排不重叠；
- 更新、删除和排序目标属于该行程，排序集合完整且无重复；
- 地点名称非空、长度合法；首版不虚构地图供应商地点 ID；
- 单项费用非负、两位小数、币种沿用行程基础币种；
- 建议后的预计费用总额不超过预算时为通过；超预算候选整体标为 `INVALID`，不静默删减；
- 文本不包含控制字符，不能把模型输出解释为 HTML；前端按普通文本渲染。

一项失败即使整个候选建议进入 `INVALID`，不会向用户展示为可确认建议。接口返回可理解的失败分类，不返回供应商原始异常、提示词或秘密。

## 生成与状态流

1. 负责人创建或更新 `DRAFT` 请求；后端验证结构并保存。
2. 负责人提交生成；事务把请求改为 `GENERATING`，随后在供应商边界外以 blocking 模式调用 Dify `POST /v1/workflows/run`。
3. 模型成功时解析并验证候选；通过则原子保存 `READY` 建议和操作，请求改为 `READY`。
4. 模型超时、限流、无效 JSON 或业务校验失败时保存失败分类；正式行程不变，用户可以修改请求后重试。
5. 同一请求同时只允许一次生成；每次重试产生新的建议记录，不覆盖历史建议。
6. 新建议生成后，之前尚未解决的建议标为 `EXPIRED`；行程版本变化时建议仍可预览，但不能确认，必须重新生成。

首版使用同步 HTTP 生成，并配置连接/响应超时、响应体上限和最多一次仅针对连接建立失败的重试。Dify 返回的 `workflow_run_id`、状态、耗时、token 数和非敏感输出元数据进入建议审计字段；API Key、节点输入、知识正文和供应商原始异常不写日志。接口状态模型保留 `GENERATING`，以便 #30 引入后台任务时不改变领域契约。

## 差异预览与原子确认

差异由后端基于当前行程快照和建议操作计算，返回每项 `ADD`、`UPDATE`、`DELETE`、`REORDER` 的前后值及依赖关系。前端不自行推断正式差异。

用户可以选择全部操作或一个满足依赖闭包的子集。`REORDER_DAY_ITEMS` 依赖该日相关的新增/删除操作；如果选择不完整，后端返回可解释的依赖错误，不自动扩大选择范围。

确认请求包含：

- 唯一 `decisionId`；
- 唯一 `commandId`；
- `expectedItineraryVersion`；
- 选中的 `operationKey` 列表。

规划应用服务在确认前重新验证负责人、建议状态、建议归属、行程版本和所选操作，并调用行程模块新增的 `applyRevision` 批量命令。行程模块在一个 MySQL 事务中锁定聚合、复验所有目标和领域规则、应用全部选中操作、把行程版本只递增一次并保存幂等命令结果。

任意操作失败则整批回滚，建议保持 `READY` 并记录不含敏感内容的失败分类；正式行程完全不变。成功后规划模块把决定与结果版本保存为 `CONFIRMED`。同一 `decisionId` 或 `commandId` 重试返回原结果，不重复修改。

## HTTP 接口

所有接口位于 `/web/itineraries/{itineraryId}/planning`，复用当前成员认证，并在服务端执行负责人权限：

- `GET /request`：读取当前规划请求草稿；
- `PUT /request`：以请求版本保存结构化草稿；
- `POST /generate`：提交草稿并生成新建议；
- `GET /proposals`：列出该请求的建议历史和状态；
- `GET /proposals/{proposalId}`：读取已校验建议和服务端差异；
- `POST /proposals/{proposalId}/confirm`：确认全部或选中的操作；
- `POST /proposals/{proposalId}/reject`：拒绝建议。

错误统一区分：输入无效、无权限、请求/建议不存在、生成中、供应商暂不可用、AI 输出无效、建议已过期、操作依赖无效、行程版本冲突和重复决定载荷不一致。

## 前端体验

在现有 `ItineraryEditor` 增加“AI 规划”入口，打开同一路由内的规划工作区：

1. **规划需求**：结构化填写目的地、日期、总预算、币种、同行人数和偏好；日期与目的地默认取当前行程，但由用户确认。
2. **生成状态**：明确显示正在生成、超时、限流、契约无效或业务校验失败；失败不会改变编辑器快照。
3. **差异预览**：按日显示新增、修改、删除和排序，展示前后值、单项费用变化和预算汇总；每项有复选框，并提供全选/全不选。
4. **确认结果**：确认期间禁用手工写操作；成功后重新加载正式行程，失败则保留选择和原行程视图。

移动端使用单列步骤流，差异卡片不依赖横向表格；状态和费用变化同时使用文字与颜色表达。所有模型文本按纯文本显示，确认按钮明确写出“确认并写入行程”。

## 测试与验收证据

- 迁移测试：dry-run、apply、重复执行、部分结构恢复、指标收敛和旧字段防回流；
- 领域测试：规划输入、输出契约、日期、时间冲突、地点、预算、选择依赖和状态转换；
- 应用测试：模型成功/超时/限流/无效输出、版本过期、拒绝、逐项确认、整批回滚及两级幂等；
- 架构测试：规划模块不能依赖行程 infrastructure，AI 适配器不能依赖行程写入接口；
- MySQL/HTTP 集成：真实事务验证确认成功、任一操作失败不产生部分写入、失败/拒绝不改变原行程；
- 固定 AI 评测集：至少覆盖多目的地、多日、预算边界、重复/冲突安排、越界日期、未知地点文本、恶意指令文本、知识检索引用和隐私字段；评测运行在可控 Dify HTTP 替身结果上，不依赖一次随机在线模型输出；
- Vue 测试：草稿、生成、失败、差异选择、过期、确认成功和确认失败；
- 浏览器关键旅程：结构化需求 → 生成建议 → 取消/拒绝保持原状 → 选择确认 → 正式行程刷新。

在线 Dify 冒烟只验证已发布工作流的输入变量、知识检索链路、输出字段和契约解析，不作为确定性 CI 的必需门禁，也不得使用真实个人数据。由于当前任务不授权部署，仓库同时提供无秘密工作流配置清单；实际创建或绑定知识库、发布工作流和注入 API Key 属于后续明确授权的部署步骤。

## 迁移、配置与恢复

新增版本化 MySQL 迁移和 `travel_share.sql` 空库结构，沿用现有 `MigrationSpec`、dry-run、差异统计和重复执行规则。没有历史规划数据，不生成合成业务建议。

新增配置只引用环境变量：`DIFY_ITINERARY_BASE_URL`、`DIFY_ITINERARY_API_KEY`、契约或工作流版本、连接/响应超时、最大操作数和最大响应字节数。生产环境可把 base URL 指向现有 Dify Nginx 的内部服务地址；代码不硬编码容器名、公网 IP、工作流 ID 或知识库 ID。真实 API Key 只由本机忽略文件或部署 Secrets 注入，不写入仓库、日志或建议元数据。

旧 `RAG_URL`、`RAG_WORKSPACE_SLUG`、`RAG_API_KEY` 只属于旧 AnythingLLM 兼容配置。删除旧写入口后从正式生产配置、Compose 契约和新文档移除这些变量，不把它们映射为 Dify 配置，也不继续维护两套同义事实源。

回滚应用版本时保留新增表；旧应用不会读取这些表。若必须停用功能，通过配置关闭 AI 规划入口，不删除规划历史，不回滚已经由用户确认并正式写入的行程变更。

## 设计决定摘要

- 采用方案 B：独立规划模块 + 供应商适配器 + 不可变修订建议；
- 首个在线适配器调用当前服务器的 Dify Workflow，由工作流接入外挂知识库；LangChain4j 直连不参与 #18 正式路径；
- 模型没有正式行程写权限；所有写入必须经用户确认和行程批量命令；
- 结构化建议可使用规划模块内部的版本化 JSON，但正式行程继续使用 #17 的规范化表；
- 逐项确认以依赖闭包为边界，所选操作在一个事务中全部成功或全部回滚；
- 确定性测试使用可控规划网关，不把在线模型随机性设为 CI 门禁。

## 参考资料

- [Dify Workflow 快速入门](https://docs.dify.ai/en/guides/application-orchestrate/creating-an-application)：工作流输入、节点编排和输出节点。
- [Dify Workflow Service API 模板](https://github.com/langgenius/dify/blob/main/web/app/components/develop/template/template_workflow.en.mdx)：`POST /v1/workflows/run` blocking 响应、运行标识、输出和 token 元数据。
- [Dify 插件类型说明](https://docs.dify.ai/en/develop-plugin/getting-started/choose-plugin-type)：知识库数据源与工作流扩展的职责边界。

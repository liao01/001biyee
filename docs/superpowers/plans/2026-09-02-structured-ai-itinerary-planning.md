# 结构化 AI 行程规划闭环实施计划

> **For Codex:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task by task. Keep `superpowers:test-driven-development`, `superpowers:systematic-debugging`, and `superpowers:verification-before-completion` active. Project rules prohibit subagents without a new explicit user confirmation, so execute in the main agent.

**Goal:** 完成 GitHub Issue #18：用户保存结构化行程规划需求，现有服务器 Dify Workflow 结合外挂知识库生成版本化建议，后端在展示前确定性校验，用户逐项或整体确认后通过 #17 的正式命令边界原子修改行程。

**Architecture:** 新增 `itineraryplanning` 模块，MySQL 保存规划需求、不可变建议、建议操作和用户决定；`DifyItineraryPlannerGateway` 只调用 `/v1/workflows/run`，不能访问行程仓储。旅行行程模块新增 `applyRevision` 批量命令，在一个事务中验证并应用选中操作。Vue 编辑器通过独立 planning HTTP 适配器和状态模块展示结构化输入、生成状态、服务端差异和确认结果。

**Tech Stack:** Java 17、Spring Boot 3.4、MyBatis、MySQL 8、Jackson、Spring `RestClient`、ArchUnit、JUnit 5、Vue 3、Vitest、Vite、Dify Workflow Service API。

**Scope constraints:** 不修改或部署服务器 Dify，不创建线上知识库或工作流，不合并 PR，不部署旅游平台。在线 Dify 冒烟仅在后续取得有效工作流 API Key 与部署授权后执行；确定性 CI 使用本地 HTTP 替身。

---

## Task 1：删除旧硬编码 RAG 入口并建立 Dify 配置事实源

**Files:**

- Delete: `business/src/main/java/com/jiawa/lyw/controller/ai/CustomerServiceController.java`
- Modify: `business/src/main/resources/application-prod.properties`
- Modify: `business/src/main/resources/application.properties.example`
- Modify: `business/src/main/resources/application.yml.example`
- Modify: `.env.example`
- Create: `business/src/test/java/com/jiawa/lyw/security/ProductionRuntimeSecretsTests.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/infrastructure/DifyItineraryPlanningProperties.java`
- Create: `business/src/test/java/com/jiawa/lyw/itineraryplanning/infrastructure/DifyItineraryPlanningPropertiesTests.java`

### Step 1：写失败测试

测试以下行为：

- Dify base URL 必须是无用户信息、无 query/fragment 的绝对 HTTP(S) 地址；
- API Key 不能为空且只从 `DIFY_ITINERARY_API_KEY` 注入；
- 连接/读取超时、最大响应字节和最大操作数有安全范围；
- 生产配置不再引用 `RAG_URL`、`RAG_WORKSPACE_SLUG`、`RAG_API_KEY`；
- 当前源码不存在旧 `/web/customerService/message` 写入口、固定 RAG URL 或硬编码 Bearer 值。

Run:

```powershell
mvn -q -pl business -Dtest=DifyItineraryPlanningPropertiesTests,ProductionRuntimeSecretsTests test
```

Expected: FAIL，因为属性类尚不存在且旧入口仍在。

### Step 2：实现最小安全配置

新增 `@ConfigurationProperties(prefix = "app.ai.itinerary.dify")`，正式字段为 `baseUrl`、`apiKey`、`contractVersion`、`connectTimeout`、`readTimeout`、`maxResponseBytes`、`maxOperations`。示例和生产配置只消费 Dify 环境变量。

删除旧 CustomerService 写入口和硬编码凭据；保留 `ChatHistoryService` 及历史读取所需领域代码，但不提供能调用旧 AnythingLLM 的 HTTP 路径。不要尝试验证或输出旧凭据。

### Step 3：运行测试并扫描

Run:

```powershell
mvn -q -pl business -Dtest=DifyItineraryPlanningPropertiesTests,ProductionRuntimeSecretsTests test
python -m unittest tests.scripts.security.test_scan_repository
```

Expected: PASS，源码及配置没有旧硬编码凭据和 RAG 正式变量。

### Step 4：提交

```powershell
git add -- .env.example business/src/main/resources/application-prod.properties business/src/main/resources/application.properties.example business/src/main/resources/application.yml.example business/src/main/java/com/jiawa/lyw/controller/ai/CustomerServiceController.java business/src/main/java/com/jiawa/lyw/itineraryplanning/infrastructure/DifyItineraryPlanningProperties.java business/src/test/java/com/jiawa/lyw/itineraryplanning/infrastructure/DifyItineraryPlanningPropertiesTests.java business/src/test/java/com/jiawa/lyw/security/ProductionRuntimeSecretsTests.java
git commit -m "security: replace legacy rag credential path (#18)"
```

## Task 2：建立规划模块数据库迁移

**Files:**

- Create: `sql/migrations/20260902_itinerary_planning.sql`
- Modify: `sql/travel_share.sql`
- Modify: `tests/scripts/migration_specs.py`
- Create: `tests/scripts/test_itinerary_planning_migration.py`

### Step 1：写迁移失败测试

登记四张正式表：

- `itinerary_planning_request`
- `itinerary_planning_destination`
- `itinerary_revision_proposal`
- `itinerary_revision_operation`
- `itinerary_revision_resolution`

虽然设计章节称“四类事实”，实现为五张表，目的地单独规范化；测试必须固定精确表、列、索引、外键、CHECK、字符集、默认 dry-run 和收敛指标。

覆盖：空库 dry-run、apply、重复执行、部分建表后恢复、非空 member/itinerary 外键、未知旧字段残留和 `travel_share.sql` 同步。

Run:

```powershell
python -m unittest tests.scripts.test_itinerary_planning_migration
```

Expected: FAIL，迁移不存在。

### Step 2：实现版本化迁移

沿用 `MigrationSpec` 和 #17 迁移模式：默认 dry-run，只有 `@apply_itinerary_planning_migration = 1` 才建缺失表；`CREATE TABLE IF NOT EXISTS` 支持中断重试；末尾输出 missing/extra table、column、index、foreign key 和 legacy residual 数量。

重要约束：

- request/version >= 1，预算非负、party_size 1–100；
- proposal 的 base itinerary version >= 1；
- operation `(proposal_id, operation_key)` 唯一，position 唯一；
- resolution `decision_id` 唯一，保存选择摘要而非第二份完整建议；
- 所有 JSON 列只属于规划模块，不向 itinerary 核心表增加 JSON。

### Step 3：验证 apply、重入和空库脚本

Run:

```powershell
python -m unittest tests.scripts.test_itinerary_planning_migration tests.scripts.test_itinerary_core_migration
```

Expected: PASS。

### Step 4：提交

```powershell
git add -- sql/migrations/20260902_itinerary_planning.sql sql/travel_share.sql tests/scripts/migration_specs.py tests/scripts/test_itinerary_planning_migration.py
git commit -m "feat: add itinerary planning migration (#18)"
```

## Task 3：定义规划需求、建议契约和确定性校验

**Files:**

- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/domain/PlanningStatus.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/domain/ProposalStatus.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/domain/RevisionOperationType.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/domain/PlanningError.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/domain/PlanningException.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/domain/PlanningModels.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/domain/RevisionProposalValidator.java`
- Create: `business/src/test/java/com/jiawa/lyw/itineraryplanning/domain/PlanningModelsTests.java`
- Create: `business/src/test/java/com/jiawa/lyw/itineraryplanning/domain/RevisionProposalValidatorTests.java`
- Create: `business/src/test/resources/itinerary-planning/evaluation-cases.json`

### Step 1：用固定评测集写失败测试

至少覆盖：

- 多目的地、多日有效建议；
- 日期越界；
- 时间只填一端、倒序和同日重叠；
- 空地点、超长地点和控制字符；
- 费用负数、超过两位小数、精度越界和总预算超限；
- 重复 operation key、未知操作、超过最大操作数；
- 更新/删除目标不属于当前快照；
- 排序目标不完整或重复；
- 未知契约版本、额外字段、Markdown 包裹和隐私字段；
- 知识引用只允许不透明 ID，不允许文档正文。

Run:

```powershell
mvn -q -pl business -Dtest=PlanningModelsTests,RevisionProposalValidatorTests test
```

Expected: FAIL，领域类型不存在。

### Step 2：实现纯 Java 领域模型

模型使用不可变 record/enum，不依赖 Spring、Jackson 或 MyBatis。校验器输入 `ItineraryModels.Snapshot`、规划需求和已解析候选，输出已验证不可变建议或抛出稳定错误码。

预算比较按行程基础币种和 `DECIMAL(14,2)` 语义。时间冲突基于应用建议后的完整日程，不只比较 AI 新增项。

### Step 3：运行领域和架构测试

Run:

```powershell
mvn -q -pl business -Dtest=PlanningModelsTests,RevisionProposalValidatorTests,ItineraryModuleBoundaryTests test
```

Expected: PASS。

### Step 4：提交

```powershell
git add -- business/src/main/java/com/jiawa/lyw/itineraryplanning/domain business/src/test/java/com/jiawa/lyw/itineraryplanning/domain business/src/test/resources/itinerary-planning/evaluation-cases.json
git commit -m "feat: define structured itinerary proposals (#18)"
```

## Task 4：扩展行程核心批量修订命令

**Files:**

- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/application/ItineraryCommands.java`
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/application/ItineraryApplicationService.java`
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/application/DefaultItineraryApplicationService.java`
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/application/ItineraryRepository.java`
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/infrastructure/ItineraryMapper.java`
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/infrastructure/MyBatisItineraryRepository.java`
- Modify: `business/src/main/resources/mapper/itinerary/ItineraryMapper.xml`
- Modify: `business/src/test/java/com/jiawa/lyw/itinerary/application/ItineraryCommandContractTests.java`
- Modify: `business/src/test/java/com/jiawa/lyw/itinerary/infrastructure/ItineraryRepositoryIT.java`

### Step 1：写批量命令失败测试

新增 `ApplyRevision`，包含已验证的新增、完整更新、删除和按日排序操作。测试：

- 命令 ID、期望版本和操作列表必填；
- 目标、日期、条目和排序集合归属正确；
- 同一批中引用新增条目时使用临时 operation key，不伪造数据库 ID；
- 全部操作成功只递增一次版本；
- 中间任意失败整批回滚；
- 相同 commandId+载荷返回 replay，相同 commandId+不同载荷冲突；
- 非负责人拒绝；已归档/取消行程拒绝。

Run:

```powershell
mvn -q -pl business -Dtest=ItineraryCommandContractTests test
```

Expected: FAIL。

### Step 2：实现应用接口和事务

在 `DefaultItineraryApplicationService.applyRevision` 使用现有命令记录、`SELECT ... FOR UPDATE`、owner 策略和 hash 事实源。先在内存快照完整模拟并验证结果，再执行仓储写入；不要循环调用公开 `addItem/updateItem` 方法，避免多事务、多版本和多命令记录。

### Step 3：真实 MySQL 原子性测试

Run:

```powershell
python -m scripts.run_backend_integration --class ItineraryRepositoryIT
```

Expected: 批量成功、回滚、重放和版本冲突全部 PASS；测试数据带 `IT-TEST-#18` 标识并由测试类清理。

### Step 4：提交

```powershell
git add -- business/src/main/java/com/jiawa/lyw/itinerary business/src/main/resources/mapper/itinerary/ItineraryMapper.xml business/src/test/java/com/jiawa/lyw/itinerary/application/ItineraryCommandContractTests.java business/src/test/java/com/jiawa/lyw/itinerary/infrastructure/ItineraryRepositoryIT.java
git commit -m "feat: apply atomic itinerary revisions (#18)"
```

## Task 5：实现规划应用接口、仓储和状态流

**Files:**

- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/application/ItineraryPlanningApplicationService.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/application/ItineraryPlannerGateway.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/application/PlanningRepository.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/application/PlanningCommands.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/application/DefaultItineraryPlanningApplicationService.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/infrastructure/PlanningRows.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/infrastructure/PlanningMapper.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/infrastructure/MyBatisPlanningRepository.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/infrastructure/ItineraryPlanningConfiguration.java`
- Create: `business/src/main/resources/mapper/itineraryplanning/PlanningMapper.xml`
- Create: `business/src/test/java/com/jiawa/lyw/itineraryplanning/application/ItineraryPlanningApplicationServiceTests.java`
- Create: `business/src/test/java/com/jiawa/lyw/itineraryplanning/infrastructure/PlanningRepositoryIT.java`

### Step 1：写状态流失败测试

覆盖草稿新建/更新的乐观版本、只能负责人访问、同一请求单次生成、READY/FAILED/INVALID、重试产生新 proposal、旧 proposal 过期、拒绝不改行程、行程版本变化时确认过期、选择依赖、decisionId 幂等和失败不写 resolution 成功结果。

### Step 2：实现应用服务

外部 Dify 调用不得处于数据库事务中：短事务领取生成权 → 提交 → 调用 gateway → 新事务写成功/失败。并发生成通过条件更新或锁确保只有一个调用取得执行权。

确认路径先读取并验证 proposal，在事务内保存决定意图并调用 `ItineraryApplicationService.applyRevision`；需要保证行程命令成功而 resolution 写入失败时可用同一 commandId 重试收敛，不把跨模块调用伪装为分布式原子事务。

### Step 3：运行应用与仓储测试

Run:

```powershell
mvn -q -pl business -Dtest=ItineraryPlanningApplicationServiceTests test
python -m scripts.run_backend_integration --class PlanningRepositoryIT
```

Expected: PASS，测试数据完整清理。

### Step 4：提交

```powershell
git add -- business/src/main/java/com/jiawa/lyw/itineraryplanning/application business/src/main/java/com/jiawa/lyw/itineraryplanning/infrastructure business/src/main/resources/mapper/itineraryplanning business/src/test/java/com/jiawa/lyw/itineraryplanning/application business/src/test/java/com/jiawa/lyw/itineraryplanning/infrastructure
git commit -m "feat: persist itinerary planning lifecycle (#18)"
```

## Task 6：实现 Dify Workflow 适配器与知识库输出契约

**Files:**

- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/infrastructure/DifyItineraryPlannerGateway.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/infrastructure/DifyWorkflowModels.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/infrastructure/RevisionContractParser.java`
- Create: `business/src/test/java/com/jiawa/lyw/itineraryplanning/infrastructure/DifyItineraryPlannerGatewayTests.java`
- Create: `business/src/test/java/com/jiawa/lyw/itineraryplanning/infrastructure/RevisionContractParserTests.java`
- Create: `docs/integrations/dify-itinerary-planning-workflow.md`

### Step 1：用本地 HTTP 替身写失败测试

断言请求：

- `POST {baseUrl}/v1/workflows/run`；
- `Authorization: Bearer ...` 不进入异常、日志或断言失败文本；
- body 只有 `inputs`、`response_mode=blocking` 和伪名 `user`；
- inputs 精确为 `planning_request_json`、`itinerary_snapshot_json`、`contract_version`；
- 不发送邮箱、真实 memberId、Cookie、access token 或私人备注（若备注被定义为私密则过滤）。

断言响应：成功、failed、缺失 `revision_json`、超大 body、未知字段、非 JSON、超时、429、5xx、workflow status 非 succeeded；只对连接建立失败做一次重试。

### Step 2：实现适配器

使用 `RestClient` 和显式 request factory timeout。先按字节上限读取，再用 Jackson 严格 DTO 解析 Dify 外层响应和 `revision_json`。记录 workflow run ID、耗时和 token 数；不记录 API Key、完整输入、知识正文或原始响应。

成员伪标识使用应用本地 secret 派生的 HMAC 或不可逆摘要并带固定前缀；不得直接传数据库 ID。若现有配置没有合适 secret，新增独立 `DIFY_ITINERARY_USER_HASH_KEY` Secret，而不是复用 JWT。

### Step 3：编写无秘密 Dify 工作流清单

文档明确：开始节点三个变量、知识检索节点绑定用户现有知识库、LLM 输出 `itinerary-revision/v1`、输出节点字段 `revision_json` 与可选 `knowledge_reference_ids`、发布后创建独立 Service API Key。文档不得包含实际服务器 IP、API Key、知识库 ID、工作流 ID或个人数据。

### Step 4：运行适配器测试

```powershell
mvn -q -pl business -Dtest=DifyItineraryPlannerGatewayTests,RevisionContractParserTests test
```

Expected: PASS。

### Step 5：提交

```powershell
git add -- business/src/main/java/com/jiawa/lyw/itineraryplanning/infrastructure business/src/test/java/com/jiawa/lyw/itineraryplanning/infrastructure docs/integrations/dify-itinerary-planning-workflow.md
git commit -m "feat: connect itinerary planning to Dify (#18)"
```

## Task 7：暴露负责人规划 HTTP 契约

**Files:**

- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/api/ItineraryPlanningController.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/api/ItineraryPlanningHttpModels.java`
- Create: `business/src/main/java/com/jiawa/lyw/itineraryplanning/api/ItineraryPlanningExceptionHandler.java`
- Modify: `business/src/main/java/com/jiawa/lyw/config/SpringMvcConfig.java`
- Create: `business/src/test/java/com/jiawa/lyw/itineraryplanning/api/ItineraryPlanningControllerTests.java`
- Create: `business/src/test/java/com/jiawa/lyw/itineraryplanning/api/ItineraryPlanningHttpIT.java`

### Step 1：写 MockMvc 失败测试

覆盖 GET/PUT request、POST generate、GET proposals/detail、POST confirm/reject；认证、owner、输入校验、错误码、幂等字段、版本冲突、Dify unavailable、invalid output、expired proposal、依赖无效。

HTTP JSON 不暴露供应商 API Key、提示词、知识正文、堆栈或数据库字段。确认响应返回 itineraryId、新 version、replayed 和 proposal 状态。

### Step 2：实现 Controller 与错误映射

复用 `CurrentMemberProvider`，不从请求接受 member ID。Controller 只负责 DTO 转换，不解析 Dify JSON、不计算差异、不访问 Mapper。

### Step 3：运行真实 HTTP 集成

```powershell
python -m scripts.run_backend_integration --class ItineraryPlanningHttpIT
```

Expected: 使用真实 MySQL、可控规划网关，验证拒绝/失败不改行程、选中确认原子成功、过期冲突及重试；清理全部 `IT-TEST-#18` 数据。

### Step 4：提交

```powershell
git add -- business/src/main/java/com/jiawa/lyw/itineraryplanning/api business/src/main/java/com/jiawa/lyw/config/SpringMvcConfig.java business/src/test/java/com/jiawa/lyw/itineraryplanning/api
git commit -m "feat: expose itinerary planning API (#18)"
```

## Task 8：实现前端 planning HTTP 与状态模块

**Files:**

- Create: `web/src/modules/itinerary-planning/itineraryPlanningHttp.js`
- Create: `web/src/modules/itinerary-planning/itineraryPlanningHttp.test.js`
- Create: `web/src/modules/itinerary-planning/itineraryPlanning.js`
- Create: `web/src/modules/itinerary-planning/itineraryPlanning.test.js`

### Step 1：写失败测试

覆盖：正式 identity/request base URL、草稿版本、生成状态、失败分类、proposal 加载、差异选择依赖、确认 decisionId/commandId 稳定重试、拒绝、过期和刷新正式行程。

模块不得把规划正文、Dify 输出或 token 写入 localStorage/sessionStorage；刷新页面从后端恢复。

Run:

```powershell
cd web
npm test -- --run src/modules/itinerary-planning/itineraryPlanningHttp.test.js src/modules/itinerary-planning/itineraryPlanning.test.js
```

Expected: FAIL。

### Step 2：实现命令状态机

使用显式状态：`idle`、`saving`、`generating`、`ready`、`confirming`、`confirmed`、`failed`、`expired`。同一动作进行中禁止重复发送；网络重试复用 UUID。

### Step 3：运行测试并提交

```powershell
npm test -- --run src/modules/itinerary-planning/itineraryPlanningHttp.test.js src/modules/itinerary-planning/itineraryPlanning.test.js
git add -- web/src/modules/itinerary-planning
git commit -m "feat: add itinerary planning client (#18)"
```

## Task 9：构建 AI 规划输入和差异确认界面

**Skills:** 使用 `ui-ux-pro-max` 与 `build-web-apps:frontend-testing-debugging`；若需要浏览器自动化，使用 `browser:control-in-app-browser`。这些技能已在当前任务读取过，实施时遵守其检查流程。

**Files:**

- Create: `web/src/modules/itinerary-planning/ItineraryPlanningPanel.vue`
- Create: `web/src/modules/itinerary-planning/ItineraryPlanningPanel.test.js`
- Create: `web/src/modules/itinerary-planning/PlanningRequestForm.vue`
- Create: `web/src/modules/itinerary-planning/PlanningDiff.vue`
- Create: `web/src/modules/itinerary-planning/itineraryPlanning.css`
- Modify: `web/src/modules/itinerary/ItineraryEditor.vue`
- Modify: `web/src/modules/itinerary/ItineraryEditorPage.test.js`

### Step 1：写组件失败测试

覆盖：

- 默认目的地/日期来自当前行程并可确认修改；
- 预算、币种、同行人数、偏好全部结构化；
- 草稿保存与恢复；
- generating、timeout、rate limit、invalid contract、invalid business output；
- 按日 ADD/UPDATE/DELETE/REORDER 前后差异；
- 全选、全不选、逐项选择和依赖提示；
- 过期建议禁用确认；
- 确认中同时禁用手工编辑；
- 成功刷新正式快照，失败保留原快照和选择；
- Dify/知识库只以“参考知识已参与生成”呈现，不假装知识内容是已验证事实。

### Step 2：实现响应式可访问界面

桌面使用编辑器右侧或可展开规划工作区，移动端单列步骤流。差异不用横向表格；增加 `aria-live`、明确按钮文案、键盘操作和非颜色状态标识。

### Step 3：组件和全量前端测试

```powershell
cd web
npm test -- --run src/modules/itinerary-planning/ItineraryPlanningPanel.test.js src/modules/itinerary/ItineraryEditorPage.test.js
npm test -- --run
npm run build
```

Expected: 全部 PASS；只允许记录既有分块大小警告。

### Step 4：浏览器检查

使用本地可控后端/夹具验证桌面和 375px：输入 → 生成 → 差异 → 拒绝保持原状 → 选择确认 → 行程刷新；检查 console、请求失败展示、水平溢出和未授权路由。测试数据使用 `IT-TEST-#18-<batch>`，结束前清理并回查。

### Step 5：提交

```powershell
git add -- web/src/modules/itinerary-planning web/src/modules/itinerary/ItineraryEditor.vue web/src/modules/itinerary/ItineraryEditorPage.test.js
git commit -m "feat: build AI itinerary planning workflow (#18)"
```

## Task 10：补齐架构、数据和 Dify 运维文档

**Files:**

- Modify: `CONTEXT.md`
- Create: `docs/adr/0004-dify-itinerary-planning-adapter.md`
- Create: `docs/data/itinerary-planning.md`
- Modify: `docs/data/itinerary.md`
- Create: `tests/scripts/test_itinerary_planning_ci_contract.py`
- Modify: `.github/workflows/ci.yml`（仅当自动发现不足）

### Step 1：更新领域词汇

正式增加或细化：规划请求、修订建议、建议操作、建议确认、生成运行、知识引用。避免把“AI 行程”“Dify 记录”“聊天回复”作为同义事实源。

### Step 2：记录 ADR 和数据链路

ADR 说明为何选择“Dify 作为可替换规划适配器、MySQL 保存领域状态”，而非 Dify 直接写行程或 LangChain4j 双正式路径。数据文档记录每张表的 writer/reader、跨模块命令、失败收敛、幂等、迁移、回滚、秘密和知识库边界。

### Step 3：增加 CI 契约测试

验证：迁移被运行、`ItineraryPlanningHttpIT` 被集成 runner 自动发现、生产 JAR 不含集成替身、正式配置只引用 Dify Secret 名、旧硬编码 RAG 入口不回流。

Run:

```powershell
python -m unittest tests.scripts.test_itinerary_planning_ci_contract
```

### Step 4：提交

```powershell
git add -- CONTEXT.md docs/adr/0004-dify-itinerary-planning-adapter.md docs/data/itinerary-planning.md docs/data/itinerary.md tests/scripts/test_itinerary_planning_ci_contract.py .github/workflows/ci.yml
git commit -m "docs: define Dify itinerary planning contract (#18)"
```

## Task 11：完成 #18 全量验证、安全门禁和 PR 更新

**Files:**

- Modify only if verification reveals a concrete defect in files already covered above.

### Step 1：后端与迁移

```powershell
python -m unittest discover -s tests -p "test_*.py"
mvn -q -pl business test
mvn -q -Pdeployment clean package
python -m scripts.run_backend_integration
```

Docker 可用时再运行：

```powershell
python -m scripts.run_backend_integration --containers
```

Expected: 全部确定性测试通过；若本机 Docker 仍不可用，明确保留本机容器浏览器证据缺口，但 GitHub CI 容器门禁必须通过。

### Step 2：前端

```powershell
cd web
npm test -- --run
npm run build
```

Expected: PASS。

### Step 3：包内容、差异和秘密

```powershell
git diff --check origin/master...HEAD
python -m unittest tests.scripts.security.test_scan_repository
```

检查生产 JAR：不得包含 `*IT.class`、Testcontainers、固定 Dify URL/API Key、测试邮箱或测试配置。审查 `origin/master...HEAD` 的完整 diff、依赖变化、二进制、子模块、符号链接和危险调用。

硬编码旧凭据已进入历史提交，当前分支删除不能使它失效。将“旧服务凭据待轮换”保留为安全阻塞，直到外部服务确认轮换或确认其早已失效；不输出凭据值。

### Step 4：提交必要修复并推送

只有验证发现范围内缺陷时创建修复提交。随后确认工作树只有预期状态，推送当前功能分支并更新 PR #33；不合并、不部署。

### Step 5：等待 GitHub Checks

确认 PR head OID 等于本地 head，五项 required checks 全部 success，PR base 仍为 `master`，无 requested changes 或冲突。失败时按 `superpowers:systematic-debugging` 处理，不重跑掩盖确定性缺陷。

### Step 6：Issue #18 验收映射

- 结构化输入：规划请求领域、HTTP、表单和持久化测试；
- 版本化输出与展示前校验：Dify 契约解析、固定评测集和 INVALID 状态；
- AI 只产生建议：架构依赖测试、Dify 适配器无写接口、失败/拒绝 HTTP IT；
- 差异与选择确认：服务端 diff、批量命令原子性、Vue 组件和浏览器旅程。

只有上述证据全部成立，且旧凭据安全阻塞得到处置，才能关闭 #18；否则保持 Issue OPEN 并继续处理，不把局部通过报告为完成。

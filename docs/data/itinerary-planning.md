# 结构化行程规划数据链路

本文记录 #18 结构化行程规划的数据口径。MySQL 中的五张规划表是规划请求、修订建议、建议操作和建议确认的唯一事实源；Dify 运行记录、知识库、HTTP 响应、日志和 Vue 状态都不是独立业务事实源。正式旅行行程仍由[旅行行程核心数据链路](itinerary.md)定义，规划模块不得直接写行程核心表。

## 正式契约

- 表结构、约束和收敛检查以 [`20260902_itinerary_planning.sql`](../../sql/migrations/20260902_itinerary_planning.sql) 为正式升级脚本；全新环境由 [`travel_share.sql`](../../sql/travel_share.sql) 提供相同契约。
- 规划模块的应用边界以 [`ItineraryPlanningApplicationService.java`](../../business/src/main/java/com/jiawa/lyw/itineraryplanning/application/ItineraryPlanningApplicationService.java) 为准，持久化端口以 [`PlanningRepository.java`](../../business/src/main/java/com/jiawa/lyw/itineraryplanning/application/PlanningRepository.java) 为准。
- Dify 只实现 [`ItineraryPlannerGateway.java`](../../business/src/main/java/com/jiawa/lyw/itineraryplanning/application/ItineraryPlannerGateway.java)；结构化输出在持久化前由后端解析和校验。
- 跨模块只能调用 `ItineraryApplicationService` 读取快照和提交已确认的原子修订命令，不得调用行程 Mapper 或直接写行程表。
- HTTP 行为以 [`ItineraryPlanningHttpIT.java`](../../business/src/test/java/com/jiawa/lyw/itineraryplanning/api/ItineraryPlanningHttpIT.java) 为可执行契约。

## 表、Writer 与 Reader

| 表 | 保存的业务事实 | Writer | Reader |
| --- | --- | --- | --- |
| `itinerary_planning_request` | 一份规划请求的负责人、关联行程、结构版本、日期、预算、人数、偏好、状态和版本 | 规划应用服务通过 `PlanningRepository` 创建或按期望版本更新草稿，并在生成前领取运行权 | 规划应用服务按负责人和行程恢复当前请求；HTTP 边界只返回授权后的视图 |
| `itinerary_planning_destination` | 规划请求中有顺序的目的地 | `PlanningRepository` 随规划请求在同一事务中替换 | 规划仓储随请求装配；Dify 适配器只接收应用服务给出的领域模型 |
| `itinerary_revision_proposal` | 针对特定行程版本的不可变修订建议、生成审计元数据或安全失败码 | 规划应用服务在外部生成结束后的新事务中写入 READY、INVALID 或 FAILED 结果 | 规划应用服务按负责人列出或读取；确认前重新核对建议状态与行程版本 |
| `itinerary_revision_operation` | 修订建议中的有序建议操作、目标、载荷、费用变化和校验结果 | `PlanningRepository` 与修订建议一次写入，创建后不更新操作正文 | 规划应用服务和 HTTP 差异视图读取；确认只选择已持久化且依赖闭合的操作 |
| `itinerary_revision_resolution` | 对一份建议的唯一建议确认或拒绝决定，以及确认所对应的行程命令结果 | 规划应用服务按决定编号写入；数据库同时约束一份建议只能解决一次 | 规划应用服务用于幂等重放和冲突判断；不得从日志或 Dify 状态推断用户决定 |

`preferences_json` 和建议操作的 `payload_json` 是各自表契约内的结构化值，不是可任意扩展的第二事实源。其解析、字段白名单和版本由规划领域模型控制；供应商原始响应不会原样入库。

## 跨模块命令与事务

保存规划请求只调用行程模块读取当前快照，以验证负责人、日期范围和基准币种。生成使用“短事务领取 → 提交 → 外部调用 → 新事务收敛”的边界：领取成功后才调用 Dify，网络等待不占用数据库事务；并发请求中只有一个运行取得生成权。

修订建议始终绑定生成时的行程版本。建议确认会再次读取正式快照，验证负责人、建议状态、期望版本、操作依赖和选择范围，然后把选中操作转换为一个 `ItineraryApplicationService.applyRevision` 命令。确认应用边界开启事务，行程命令和规划仓储加入同一事务，全部条目变更、版本递增、命令结果与确认决定一起提交；任一数据库写入失败则整批回滚。规划模块不直接写 `itinerary`、`itinerary_day` 或 `itinerary_item`。确认前发现版本过期时保留建议过期标记并返回领域错误，此时尚未应用行程命令。

## 幂等与失败收敛

- 规划请求以请求版本防止旧页面覆盖新草稿；生成领取使用条件更新，重复或并发生成不会产生两个外部调用。
- 每个建议确认或拒绝携带稳定的决定 UUID。一致重放返回原结果；同一 UUID 被不同成员、建议、决定类型或选择集合复用时产生幂等冲突。
- 确认另带行程命令 UUID，复用 #17 的行程命令幂等结果。建议仅允许解决一次；已确认、已拒绝或已过期的建议不能再次决定。
- Dify 限流、超时和不可用分别收敛为安全失败码；无效契约或违反日期、时间、预算、目标归属和依赖规则的输出收敛为 INVALID。失败只保存非敏感分类，不保存供应商异常、原始响应或知识正文。
- 当正式行程版本已变化，建议收敛为 EXPIRED，用户必须基于新快照重新生成；系统不得自动覆盖较新的手工或协作修改。

## 迁移、恢复与回滚

升级脚本默认 dry-run，报告缺失表、额外表、字段、索引、外键和旧字段残留；只有显式设置 `@apply_itinerary_planning_migration = 1` 才创建缺失表。脚本使用 `CREATE TABLE IF NOT EXISTS`，可重复执行，并能在前次只创建部分表后继续收敛。CI 在随机前缀的隔离 MySQL schema 中验证 dry-run、重复执行、部分恢复，以及全新初始化 SQL 与升级脚本一致，结束后精确删除该 schema。

该迁移只新增规划表，不生成规划请求或修订建议，也不修改正式行程。上线前应备份数据库并保存迁移前 schema 证据。若尚无规划数据，可按外键逆序移除五张规划表并恢复旧应用；若已产生数据，不得删表回滚，应恢复备份到隔离库、核对每张表数量，并通过评审后的前向迁移修复。共享或生产环境执行迁移、回滚或数据修复都需要独立授权。

## 秘密与知识库边界

生产配置只引用部署环境注入的 Dify URL、Workflow API Key、成员伪名 HMAC Secret 和非敏感运行参数。真实秘密不得写入仓库、数据库、建议元数据、日志、截图、Issue 或 PR；API Key 只授权规划 Workflow，并按运维流程轮换。

Dify 接收最小化的结构化规划请求、去身份化的行程快照、契约版本和不可逆成员伪名，不接收 Cookie、访问令牌、邮箱、真实成员 ID 或行程条目的私人备注。外挂知识库的正文只留在 Dify 检索链路；平台最多保存不透明知识引用，界面只提示“参考知识已参与生成”，不展示内部 URL、知识库 ID 或未经核验的原文。平台不读取 Dify 的 PostgreSQL、Redis、向量库或数据卷。

## 2026-09-05 验收进展（未完成）

本轮修复了连接失败重试与超时分类、地点控制字符校验、确认决定保存失败后的事务回滚，以及部分选择导致预算超限的问题。固定评测集的 26 个案例已通过本地 Dify HTTP 替身、契约解析和领域校验，覆盖输出合法性；它不证明线上模型或知识库的事实准确性。

- `mvn -q -Pdeployment clean package` 已通过，业务模块普通测试 130 项、0 失败；生成的生产 Jar 未包含集成测试类、Testcontainers/Docker Java、评测夹具或本机配置。
- 真实回环 MySQL 集成测试最新运行 54 项、0 失败、1 项 Redis 容器测试按环境跳过；确认决定写入失败的回归已验证行程版本、条目及命令结果全部回滚；并发相同确认收敛为一条决定、一条行程命令和一次行程变更，另一请求按幂等重放返回。隔离库、测试账号与故障触发器由测试清理。
- 前端测试 33 文件、111 项通过，生产构建通过；仍有既有大包警告。
- 工作树秘密扫描 0 项；这不代表历史凭证已经失效，旧服务凭证仍须由外部服务确认轮换或撤销。

生成会在领取前完成行程快照读取和建议 ID 分配，前置读取或 ID 生成失败不会把请求留在 `GENERATING`；领取后的未分类生成异常会保存为不含供应商细节的 `FAILED/PROVIDER_UNAVAILABLE`，请求可按新版本再次生成。剩余验收：完整规格与 diff 审查、最新提交的远端 required checks。上述事项未完成前 #18 保持 OPEN。在线 Dify 冒烟、合并和部署尚未执行。

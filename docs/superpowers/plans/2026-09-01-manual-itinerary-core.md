# 手工行程核心实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:test-driven-development` for every behavior change and `superpowers:verification-before-completion` before claiming completion. Execute this plan task-by-task; do not merge or deploy.

**目标：** 完成 GitHub Issue #17，让已登录用户能够创建、查看和编辑以行程为核心的旅行计划，并以稳定的领域命令承接后续协作、地图、预算和 AI 提案能力。

**架构：** 在现有 Spring Boot 模块化单体中新增深模块 `itinerary`，以 MySQL 规范化表作为唯一正式事实源。应用服务承担事务、权限、幂等和乐观锁，领域层承担日期、时间冲突、排序和状态机规则，HTTP 层只做协议转换。Vue 端使用独立 HTTP 适配器与串行命令队列，每次用户动作自动保存，服务端失败时回滚本地投影。

**技术栈：** Java 17、Spring Boot、MyBatis、MySQL 8、JUnit 5、Testcontainers、ArchUnit、Vue 3、Vue Router、Axios、Vitest、Vue Test Utils。

**规格：** `docs/superpowers/specs/2026-09-01-manual-itinerary-core-design.md`

## 全局约束

- 本计划只实现 #17；不提前实现 #18–#32，但必须保留明确的模块边界供后续能力调用。
- 所有写请求都携带 UUID `commandId` 和 `expectedVersion`；创建请求固定使用 `expectedVersion = 0`。
- `itinerary`、`itinerary_destination`、`itinerary_day`、`itinerary_item`、`itinerary_command` 是正式事实源，不增加 JSON 行程副本。
- 当前仅行程所有者可读写；权限判断统一经过 `ItineraryAccessPolicy`，#21 只能替换或扩展该策略。
- 日期区间使用行程自己的 IANA 时区解释；条目时间不允许跨午夜，有时间时必须同时提交开始和结束，区间按 `[start, end)` 检测重叠。
- 删除条目使用软删除；缩短日期时，只要被排除日期仍有未删除条目就拒绝，不隐式删除。
- 测试创建的数据统一使用 `IT-TEST-#17-` 前缀和测试批次 UUID；集成测试结束后按批次精确删除并回查。
- 每个任务都遵循红—绿—重构：先运行新增测试确认按预期失败，再写最小实现，再运行聚焦测试和相邻回归测试。
- 不合并、不部署；提交只进入当前 `codex/20260829-intelligent-travel-platform` 分支。

---

## Task 1：建立可重复执行的行程数据库迁移

**文件：**

- Create: `sql/migrations/20260901_itinerary_core.sql`
- Modify: `sql/travel_share.sql`
- Create: `tests/scripts/test_itinerary_core_migration.py`
- Modify: `tests/scripts/migration_specs.py`

### Step 1：先写迁移契约测试

在 `test_itinerary_core_migration.py` 中复用 `mysql_migration_harness.py`，覆盖：

- 全新库执行后五张表、外键、索引、唯一键存在。
- `itinerary.version` 初始值为 `1`，业务字段使用明确字符集和 UTC 审计时间。
- `itinerary_command.command_id` 全局唯一，并保存 `member_id`、`operation`、可空 `itinerary_id`、`request_hash`、`result_itinerary_id`、可空 `result_item_id` 和 `result_version`。
- 同一迁移连续执行两次收敛到相同结构。
- 预先只创建其中两张表的部分状态，再执行迁移仍能收敛。
- `travel_share.sql` 的初始化结构与迁移后的结构一致。

把规格加入 `migration_specs.py` 的正式列表：

```python
MigrationSpec(
    name="itinerary_core",
    migration="sql/migrations/20260901_itinerary_core.sql",
    required_tables=(
        "itinerary", "itinerary_destination", "itinerary_day",
        "itinerary_item", "itinerary_command",
    ),
)
```

### Step 2：运行测试并确认红灯

Run:

```powershell
python -m unittest tests.scripts.test_itinerary_core_migration -v
```

Expected: FAIL，原因是迁移文件或五张表尚不存在；不得因测试发现失败。

### Step 3：实现幂等 DDL

迁移必须包含：

- `itinerary(id, owner_member_id, title, start_date, end_date, time_zone, base_currency, status, version, created_at, updated_at)`。
- `itinerary_destination(id, itinerary_id, name, country_code, time_zone, position, created_at, updated_at)`；每个行程至少一项，第一项即主目的地。
- `itinerary_day(id, itinerary_id, day_date, created_at, updated_at)`，唯一键 `(itinerary_id, day_date)`。
- `itinerary_item(id, itinerary_id, itinerary_day_id, title, place_name, start_time, end_time, notes, estimated_cost, position, deleted_at, created_at, updated_at)`；金额币种来自行程 `base_currency`，不复制币种列。
- `itinerary_command(id, command_id, member_id, operation, itinerary_id, expected_version, request_hash, result_itinerary_id, result_item_id, result_version, created_at)`；`itinerary_id` 创建时可空，`result_item_id` 仅创建安排项时有值。
- 查询路径索引：所有者+更新时间、行程+位置、日期+位置；`command_id` 使用全局唯一索引。
- 迁移前后输出差异统计；考虑 MySQL DDL 隐式提交，逐语句使用 `IF NOT EXISTS`，补列/索引使用 `information_schema` + 动态 SQL，确保中断后可重复执行并从半完成状态恢复。

同步 `travel_share.sql`，不让种子脚本继续生成旧结构。

### Step 4：验证迁移可收敛

Run:

```powershell
python -m unittest tests.scripts.test_itinerary_core_migration tests.scripts.test_mysql_migration_harness -v
python -m unittest discover -s tests/scripts -p "test_*migration*.py" -v
```

Expected: PASS；第二次执行报告零缺失、零多出、零旧结构残留。

### Step 5：提交

```powershell
git add sql/migrations/20260901_itinerary_core.sql sql/travel_share.sql tests/scripts/migration_specs.py tests/scripts/test_itinerary_core_migration.py
git commit -m "feat: add itinerary core migration (#17)"
```

---

## Task 2：定义框架无关的行程领域规则

**文件：**

- Create: `business/src/main/java/com/jiawa/lyw/itinerary/domain/ItineraryStatus.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerary/domain/ItineraryError.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerary/domain/ItineraryException.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerary/domain/ItineraryRules.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerary/domain/ItineraryModels.java`
- Create: `business/src/test/java/com/jiawa/lyw/itinerary/domain/ItineraryRulesTests.java`
- Create: `business/src/test/java/com/jiawa/lyw/architecture/ItineraryModuleBoundaryTests.java`

### Step 1：写领域测试和架构测试

测试固定覆盖：

- 接受合法 IANA 时区、ISO 4217 币种与可空 ISO 3166-1 alpha-2 国家码，拒绝别名、空值及未知代码。
- 创建日期包含首尾两天且最长 366 天；延长日期返回应补齐的日期；缩短遇到非空排除日抛出 `DATE_RANGE_CONTAINS_ITEMS`。
- 无时间条目允许共存；有时间条目必须成对提交且 `end > start`。
- `[09:00, 10:00)` 与 `[10:00, 11:00)` 不冲突，任何真实交集均抛出 `TIME_CONFLICT`。
- 目标排序和条目排序必须是当前 ID 的完整、不重复排列。
- 状态迁移严格等于规格图；`DRAFT → PLANNED` 额外要求至少一个目的地、有效日期和至少一个合法未删除安排项。
- 使用固定当地日期验证状态建议：行前不建议开始，区间内 `PLANNED` 建议 `IN_PROGRESS`，结束后 `IN_PROGRESS` 建议 `COMPLETED`；建议只返回值而不修改聚合。
- `..itinerary.domain..` 只能依赖 `java..` 和自身；模块外代码不得依赖 `..itinerary.infrastructure..`。

核心 API 固定为：

```java
public final class ItineraryRules {
    public static ZoneId requireTimezone(String value);
    public static Currency requireCurrency(String value);
    public static List<LocalDate> dates(LocalDate start, LocalDate end);
    public static void assertShrinkIsSafe(
            LocalDate newStart, LocalDate newEnd, Collection<ItineraryModels.Day> currentDays);
    public static void assertTimeRange(LocalTime start, LocalTime end);
    public static void assertNoOverlap(
            Long ignoredItemId, LocalTime start, LocalTime end,
            Collection<ItineraryModels.Item> items);
    public static void assertPermutation(Collection<Long> expected, List<Long> actual);
    public static void assertTransition(ItineraryModels.Snapshot snapshot,
                                        ItineraryStatus to);
    public static Set<ItineraryStatus> allowedTransitions(ItineraryStatus from);
    public static boolean meetsPlanningMinimum(ItineraryModels.Snapshot snapshot);
    public static Optional<ItineraryStatus> suggestedStatus(
            ItineraryModels.Snapshot snapshot, LocalDate localToday);
}
```

### Step 2：确认测试红灯

```powershell
.\business\mvnw.cmd -pl business -Dtest=ItineraryRulesTests,ItineraryModuleBoundaryTests test
```

Expected: FAIL，原因是领域类型尚不存在。

### Step 3：实现最小领域模型

`ItineraryModels` 使用嵌套不可变 record 定义 `Destination`、`Day`、`Item`、`Snapshot`、`Summary`；金额使用 `BigDecimal`，日期时间使用 `java.time`，不引入 Spring/MyBatis/Jackson 注解。

`ItineraryError` 固定为可公开的稳定代码：

```java
INVALID_ITINERARY, INVALID_DESTINATION, INVALID_ITEM, TIME_CONFLICT,
DATE_RANGE_CONTAINS_ITEMS, ITINERARY_NOT_FOUND, VERSION_CONFLICT,
IDEMPOTENCY_CONFLICT, INVALID_STATUS_TRANSITION
```

### Step 4：运行聚焦测试

```powershell
.\business\mvnw.cmd -pl business -Dtest=ItineraryRulesTests,ItineraryModuleBoundaryTests test
```

Expected: PASS。

### Step 5：提交

```powershell
git add business/src/main/java/com/jiawa/lyw/itinerary/domain business/src/test/java/com/jiawa/lyw/itinerary/domain business/src/test/java/com/jiawa/lyw/architecture/ItineraryModuleBoundaryTests.java
git commit -m "feat: define itinerary domain rules (#17)"
```

---

## Task 3：建立应用端口、权限策略和命令契约

**文件：**

- Create: `business/src/main/java/com/jiawa/lyw/itinerary/application/ItineraryApplicationService.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerary/application/ItineraryCommands.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerary/application/ItineraryAccessPolicy.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerary/application/OwnerOnlyItineraryAccessPolicy.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerary/application/ItineraryCommandHasher.java`
- Create: `business/src/test/java/com/jiawa/lyw/itinerary/application/ItineraryCommandContractTests.java`

### Step 1：写端口契约测试

测试以下行为：命令 UUID 必填、版本不能为负、创建版本只能为零、行程标题/目的地/安排标题/地点名/备注长度受限、预计金额必须为 `DECIMAL(14,2)` 可表达的非负值、目标和条目 ID 列表不能重复、规范化后的等价载荷生成相同 SHA-256、不同载荷生成不同摘要、所有者策略拒绝非 owner。

应用入口固定为：

```java
public interface ItineraryApplicationService {
    ItineraryModels.PageSlice<ItineraryModels.Summary> list(
            long actorMemberId, Set<ItineraryStatus> statuses,
            String cursor, int limit);
    ItineraryModels.Snapshot get(long actorMemberId, long itineraryId);
    CommandResult create(long actorMemberId, CommandEnvelope<CreateItinerary> command);
    CommandResult updateOverview(long actorMemberId, long itineraryId,
                                 CommandEnvelope<UpdateOverview> command);
    CommandResult replaceDestinations(long actorMemberId, long itineraryId,
                                      CommandEnvelope<ReplaceDestinations> command);
    CommandResult addItem(long actorMemberId, long itineraryId,
                          CommandEnvelope<AddItem> command);
    CommandResult updateItem(long actorMemberId, long itineraryId, long itemId,
                             CommandEnvelope<UpdateItem> command);
    CommandResult deleteItem(long actorMemberId, long itineraryId, long itemId,
                             CommandEnvelope<DeleteItem> command);
    CommandResult reorderItems(long actorMemberId, long itineraryId,
                               CommandEnvelope<ReorderItems> command);
    CommandResult transition(long actorMemberId, long itineraryId,
                             CommandEnvelope<TransitionStatus> command);
}
```

其中：

```java
public record CommandEnvelope<T>(UUID commandId, long expectedVersion, T payload) {}
public record CommandResult(long itineraryId, Long itemId,
                            long version, boolean replayed) {}
```

### Step 2：确认测试红灯

```powershell
.\business\mvnw.cmd -pl business -Dtest=ItineraryCommandContractTests test
```

### Step 3：实现端口和最小校验

`ItineraryCommandHasher` 使用专用、排序稳定的 Jackson `ObjectMapper` 仅对命令 DTO 计算 SHA-256；不得包含 actor、令牌或日志上下文。`OwnerOnlyItineraryAccessPolicy` 只比较 actor 与 owner，错误消息不泄露资源归属。

### Step 4：验证

```powershell
.\business\mvnw.cmd -pl business -Dtest=ItineraryCommandContractTests,ItineraryModuleBoundaryTests test
```

### Step 5：提交

```powershell
git add business/src/main/java/com/jiawa/lyw/itinerary/application business/src/test/java/com/jiawa/lyw/itinerary/application
git commit -m "feat: define itinerary application contracts (#17)"
```

---

## Task 4：实现 MyBatis 存储与创建、列表、详情读取

**文件：**

- Create: `business/src/main/java/com/jiawa/lyw/itinerary/infrastructure/ItineraryMapper.java`
- Create: `business/src/main/resources/mapper/itinerary/ItineraryMapper.xml`
- Create: `business/src/main/java/com/jiawa/lyw/itinerary/infrastructure/ItineraryConfiguration.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerary/infrastructure/MyBatisItineraryRepository.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerary/application/DefaultItineraryApplicationService.java`
- Create: `business/src/test/java/com/jiawa/lyw/itinerary/infrastructure/ItineraryRepositoryIT.java`

### Step 1：写真实 MySQL 集成测试

使用 `MySqlIntegrationDatabase` 与迁移脚本，按测试批次创建两个 member，覆盖：

- 创建同时写入行程、按日期生成 day、写入有序 destination、写入 command 结果，初始版本为 1。
- 同一 `commandId`、member、operation 与同一 hash 重放返回原结果且不新增行程；同 UUID 不同请求抛出 `IDEMPOTENCY_CONFLICT`。
- 列表只返回 actor 自己的行程，并按 `updated_at DESC, id DESC` 稳定游标分页。
- 详情一次组装 destination/day/item，软删除 item 不出现。
- 非所有者读取返回统一 `NOT_FOUND`，避免枚举资源。
- 测试 finally 按批次精确删除五张行程表和 member 数据并查询确认计数为零。

### Step 2：运行并确认红灯

```powershell
.\business\mvnw.cmd -pl business -Pintegration -Dit.test=ItineraryRepositoryIT verify
```

### Step 3：实现事务内创建与读取

- `DefaultItineraryApplicationService` 的写方法标注 `@Transactional`。
- 首次执行依靠全局唯一 `command_id` 收敛并发；唯一键冲突后等待首事务完成并重新读取命令记录，member、operation、hash 相同则重放，否则拒绝。
- 详情读取按四次固定查询组装，禁止 day × item × destination 笛卡尔积。
- 创建失败必须回滚业务数据和命令记录。
- `ItineraryConfiguration` 只暴露 `ItineraryApplicationService` 与 `ItineraryAccessPolicy` bean；mapper/repository 保持模块内部。

### Step 4：验证

```powershell
.\business\mvnw.cmd -pl business -Pintegration -Dit.test=ItineraryRepositoryIT verify
.\business\mvnw.cmd -pl business -Dtest=ItineraryRulesTests,ItineraryCommandContractTests,ItineraryModuleBoundaryTests test
```

### Step 5：提交

```powershell
git add business/src/main/java/com/jiawa/lyw/itinerary business/src/main/resources/mapper/itinerary business/src/test/java/com/jiawa/lyw/itinerary/infrastructure
git commit -m "feat: persist and query itineraries (#17)"
```

---

## Task 5：实现概览、目的地和日期区间修改

**文件：**

- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/application/DefaultItineraryApplicationService.java`
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/infrastructure/ItineraryMapper.java`
- Modify: `business/src/main/resources/mapper/itinerary/ItineraryMapper.xml`
- Create: `business/src/test/java/com/jiawa/lyw/itinerary/application/ItineraryOverviewIT.java`

### Step 1：写失败测试

覆盖：

- 更新标题、日期、时区、基础币种后版本只增加一次。
- `UPDATE itinerary ... WHERE id = ? AND version = ?` 影响零行时抛 `VERSION_CONFLICT`，整个事务无部分更新。
- 延长首尾日期只增加缺失 day，保留已有 day ID 和条目。
- 缩短空白日期删除对应 day；被排除日有未删除 item 时完整回滚。
- 替换目的地后 `position` 按 1024 倍数排列且至少保留一项；#17 的安排项不建立目的地外键，后续正式地点引用必须经兼容检查扩展。
- 命令重放不再次加版本。

### Step 2：确认红灯

```powershell
.\business\mvnw.cmd -pl business -Pintegration -Dit.test=ItineraryOverviewIT verify
```

### Step 3：实现最小事务逻辑

每个命令按以下顺序执行：读取历史命令 → 锁定行程 → 权限检查 → 版本检查 → 领域校验 → 子表差量更新 → 条件版本更新 → 保存命令结果。所有时间戳由一个注入的 `Clock` 产生。

### Step 4：验证并提交

```powershell
.\business\mvnw.cmd -pl business -Pintegration -Dit.test=ItineraryOverviewIT,ItineraryRepositoryIT verify
git add business/src/main/java/com/jiawa/lyw/itinerary business/src/main/resources/mapper/itinerary business/src/test/java/com/jiawa/lyw/itinerary/application/ItineraryOverviewIT.java
git commit -m "feat: edit itinerary overview and destinations (#17)"
```

---

## Task 6：实现条目新增、编辑、软删除和排序

**文件：**

- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/application/DefaultItineraryApplicationService.java`
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/infrastructure/ItineraryMapper.java`
- Modify: `business/src/main/resources/mapper/itinerary/ItineraryMapper.xml`
- Create: `business/src/test/java/com/jiawa/lyw/itinerary/application/ItineraryItemIT.java`

### Step 1：写失败测试

覆盖：

- 向指定 day 末尾新增无时间条目；新增有时间条目时存储 `TIME`，展示日期来自 day。
- day、destination 必须属于当前 itinerary。
- 相邻半开区间可保存，重叠、缺失一端、结束不晚于开始、跨午夜均拒绝。
- 更新条目时忽略自身再检测冲突。
- 软删除填充 `deleted_at`，重复删除通过同一 command 重放，不重复加版本。
- reorder 请求必须包含该 day 所有未删除 item ID 且无重复；SQL 批量写入从 1024 开始的间隔 `position`，间隔不足或接近上限时按 1024 倍数重编号。
- 任一校验或并发冲突失败，条目顺序和版本都不改变。

### Step 2：确认红灯

```powershell
.\business\mvnw.cmd -pl business -Pintegration -Dit.test=ItineraryItemIT verify
```

### Step 3：实现并保持单命令单版本

一个条目命令无论修改几张子表，行程 `version` 只从 N 变为 N+1。响应返回新版本，创建条目时额外返回 `itemId`；完整 snapshot 仍只由读取端点返回。

### Step 4：验证并提交

```powershell
.\business\mvnw.cmd -pl business -Pintegration -Dit.test=ItineraryItemIT,ItineraryOverviewIT verify
git add business/src/main/java/com/jiawa/lyw/itinerary business/src/main/resources/mapper/itinerary business/src/test/java/com/jiawa/lyw/itinerary/application/ItineraryItemIT.java
git commit -m "feat: manage ordered itinerary items (#17)"
```

---

## Task 7：实现生命周期迁移与 HTTP API

**文件：**

- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/application/DefaultItineraryApplicationService.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerary/api/ItineraryController.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerary/api/ItineraryExceptionHandler.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerary/api/ItineraryHttpModels.java`
- Create: `business/src/test/java/com/jiawa/lyw/itinerary/api/ItineraryHttpIT.java`

### Step 1：写端到端 HTTP 测试

在真实 MySQL + Spring 随机端口中使用 identity 登录获取 access token，覆盖：

- `GET /web/itineraries?status=&cursor=&limit=`、`POST /web/itineraries`、`GET /web/itineraries/{itineraryId}`；列表默认排除 `ARCHIVED`。
- `PATCH /web/itineraries/{itineraryId}`、`PUT /web/itineraries/{itineraryId}/destinations`。
- `POST /web/itineraries/{itineraryId}/items`、`PATCH/DELETE /web/itineraries/{itineraryId}/items/{itemId}`。
- `PUT /web/itineraries/{itineraryId}/days/{dayId}/item-order`、`POST /web/itineraries/{itineraryId}/status-transitions`。
- 未登录为 401；非 owner 与不存在资源返回相同 404；校验错误 400；版本冲突 409；command 重用不同载荷 409。
- 相同 command 重放响应含 `replayed: true`；所有响应加 `Cache-Control: no-store`。
- 状态建议和合法状态图完全一致，非法迁移不改变版本。
- 详情响应同时返回 `allowedTransitions` 和可空 `suggestedStatus`；服务使用可注入的 `Clock` 按行程 `time_zone` 计算当地日期，测试固定时钟，建议读取不产生写入。
- controller 日志与异常响应不含 access token、command 原始载荷或用户备注。

统一写请求外壳：

```json
{
  "commandId": "4d8f7c68-3420-45f9-b215-4a665a76fbfd",
  "expectedVersion": 3,
  "payload": {}
}
```

统一写响应数据：

```json
{
  "itineraryId": "123",
  "itemId": null,
  "version": 4,
  "replayed": false
}
```

创建安排项时 `itemId` 为新 ID，其他命令为 `null`；完整快照只由详情 GET 返回。

### Step 2：确认红灯

```powershell
.\business\mvnw.cmd -pl business -Pintegration -Dit.test=ItineraryHttpIT verify
```

### Step 3：实现协议转换和状态迁移

- Controller 用现有 `CurrentMemberProvider.memberId()` 取得 actor，不接受客户端 member ID。
- Bean Validation 只校验协议形状；领域规则仍由模块内执行。
- `ItineraryExceptionHandler` 只映射稳定错误码、固定安全消息和 HTTP 状态。
- 所有 ID 在 JSON 中输出字符串，避免 JavaScript 整数精度损失。

### Step 4：验证并提交

```powershell
.\business\mvnw.cmd -pl business -Pintegration -Dit.test=ItineraryHttpIT,IdentityHttpIT verify
.\business\mvnw.cmd -pl business test
git add business/src/main/java/com/jiawa/lyw/itinerary business/src/test/java/com/jiawa/lyw/itinerary/api
git commit -m "feat: expose itinerary command API (#17)"
```

---

## Task 8：实现前端 HTTP 适配器和串行命令队列

**文件：**

- Create: `web/src/modules/itinerary/itineraryHttp.js`
- Create: `web/src/modules/itinerary/itineraryHttp.test.js`
- Create: `web/src/modules/itinerary/itineraryEditor.js`
- Create: `web/src/modules/itinerary/itineraryEditor.test.js`
- Create: `web/src/modules/itinerary/itineraryFormatters.js`
- Create: `web/src/modules/itinerary/itineraryFormatters.test.js`

### Step 1：写前端单元测试

覆盖：

- HTTP 适配器只暴露语义方法，正确发送 Bearer token、commandId、expectedVersion，并规范化字符串 ID。
- 编辑器每个动作生成一次 UUID，队列严格串行；后续命令使用前一响应的新 version。
- 同一命令网络超时重试复用 UUID，不生成新 UUID。
- 成功后保留乐观投影并采用响应中的新 version；失败回滚到该动作前 snapshot，详情重载时才以完整服务端 snapshot 替换投影。
- 409 进入 `conflict` 状态、停止后续命令并触发重新加载；普通失败进入 `error`，用户可重试。
- 状态为 `idle/saving/saved/error/conflict`，连续动作不会短暂错误显示“已保存”。
- 时区日期和金额格式化不依赖浏览器本地时区。
- 队列仍有在途命令时注册离开确认，队列排空后立即移除监听；失败命令不得被视为已保存。

编辑器接口固定为：

```js
export function createItineraryEditor({ initialSnapshot, api, uuid }) {
  return {
    state,
    updateOverview(patch), replaceDestinations(destinations),
    addItem(item), updateItem(itemId, patch), deleteItem(itemId),
    reorderItems(dayId, itemIds), transition(toStatus),
    reload(), retryFailed()
  }
}
```

### Step 2：确认红灯

```powershell
npm --prefix web test -- src/modules/itinerary/itineraryHttp.test.js src/modules/itinerary/itineraryEditor.test.js src/modules/itinerary/itineraryFormatters.test.js
```

### Step 3：实现最小适配器和队列

复用 `identitySession.js`/`request.js` 的正式身份与 base URL 事实源；不得新建第二套 token 存储。命令队列只保存内存状态，不用 localStorage 缓存未加密的行程正文。

### Step 4：验证并提交

```powershell
npm --prefix web test -- src/modules/itinerary/itineraryHttp.test.js src/modules/itinerary/itineraryEditor.test.js src/modules/itinerary/itineraryFormatters.test.js
git add web/src/modules/itinerary
git commit -m "feat: add itinerary autosave command client (#17)"
```

---

## Task 9：实现行程列表、创建页和编辑器界面

**文件：**

- Create: `web/src/modules/itinerary/ItineraryList.vue`
- Create: `web/src/modules/itinerary/ItineraryCreate.vue`
- Create: `web/src/modules/itinerary/ItineraryEditor.vue`
- Create: `web/src/modules/itinerary/ItineraryList.test.js`
- Create: `web/src/modules/itinerary/ItineraryCreate.test.js`
- Create: `web/src/modules/itinerary/ItineraryEditor.test.js`
- Create: `web/src/modules/itinerary/itineraryRoutes.js`
- Create: `web/src/modules/itinerary/itinerary.css`
- Modify: `web/src/router/index.js`
- Modify: `web/src/components/the-sider.vue`

### Step 1：写组件和路由测试

覆盖：

- `/itineraries` 列出标题、日期、主要目的地、状态和更新时间，并可进入创建页/编辑页。
- `/itineraries/new` 验证标题、日期、时区和基础币种；提交成功跳转到 `/itineraries/:id`。
- 编辑页按 day 分组，支持元数据、目的地、条目新增/编辑/删除、上移/下移和状态迁移。
- 按钮操作与键盘操作都能调整顺序；焦点不丢失；ARIA live 区域播报保存状态和错误。
- 有时间与无时间条目清晰区分；冲突、版本冲突和缩短日期失败显示可操作错误。
- 删除前二次确认；不允许拖拽成为唯一排序方式。
- 未认证访问由现有 identity 路由守卫处理，不能复制第二套登录判断。

### Step 2：确认红灯

```powershell
npm --prefix web test -- src/modules/itinerary/ItineraryList.test.js src/modules/itinerary/ItineraryCreate.test.js src/modules/itinerary/ItineraryEditor.test.js
```

### Step 3：实现界面

- 复用 `styles/tokens.css` 和 `styles/components.css`，新增样式仅放在 `itinerary.css`。
- 桌面端 day 栏与详情区自适应布局，窄屏改为单列；不为 #17 引入地图、预算汇总或 AI 面板占位实现。
- 侧边栏新增一个正式入口“我的行程”，以 `itineraryRoutes.js` 为路由事实源。

### Step 4：验证可访问行为和构建

```powershell
npm --prefix web test -- src/modules/itinerary/ItineraryList.test.js src/modules/itinerary/ItineraryCreate.test.js src/modules/itinerary/ItineraryEditor.test.js src/components/AppShell.test.js
npm --prefix web run build
```

Expected: 测试与生产构建 PASS，无 Vue warning。

### Step 5：提交

```powershell
git add web/src/modules/itinerary web/src/router/index.js web/src/components/the-sider.vue
git commit -m "feat: build manual itinerary editor (#17)"
```

---

## Task 10：补齐事实源文档、CI 与完整验收

**文件：**

- Create: `docs/data/itinerary.md`
- Create: `docs/adr/0003-itinerary-command-aggregate.md`
- Modify: `CONTEXT.md`
- Modify: `.github/workflows/ci.yml`
- Modify: `scripts/run_backend_integration.py`（仅当当前 runner 未自动包含 `ItineraryHttpIT`）

### Step 1：先写 CI/文档契约检查

将以下断言加入现有契约测试或新建 `tests/scripts/test_itinerary_ci_contract.py`：CI 会运行 itinerary 迁移测试、Java `*IT`、全部 web 测试和构建；数据文档必须指向五张正式表且不声明 JSON 副本为事实源。

```powershell
python -m unittest tests.scripts.test_itinerary_ci_contract -v
```

Expected: 修改 CI 前 FAIL。

### Step 2：更新领域和数据事实源

- `CONTEXT.md` 增加行程、日程日、行程条目、目的地、命令、版本、所有者的统一术语，不复制完整状态枚举。
- `docs/data/itinerary.md` 记录字段口径、写入者、读取者、数据流、事务边界、软删除、审计时间、幂等与版本规则、迁移/回滚/恢复策略。
- ADR 记录选择“规范化聚合 + 命令边界”而非通用 CRUD/JSON blob 的原因、后果及 #18–#32 的扩展边界。
- CI 使用现有 runner 自动发现 `*IT`；只有发现规则不包含新测试时才修改 runner，不维护第二份测试清单。

### Step 3：执行完整验证

```powershell
python -m unittest discover -s tests/scripts -p "test_*.py" -v
.\business\mvnw.cmd -Pdeployment test
.\business\mvnw.cmd -pl business -Pintegration verify
npm --prefix web test
npm --prefix web run build
python scripts/security/scan_repository.py . --all-refs
```

Expected:

- Python、Java、真实 MySQL 集成、Vue 测试和构建全部 PASS。
- 部署 JAR 不包含 `application.properties`、`application.yml` 或 example 配置。
- 当前工作树与全部 refs 秘密扫描均为 0。
- 集成测试结束后查询 `IT-TEST-#17-` 批次残留为 0。

### Step 4：浏览器验收

在隔离 Docker 栈启动后，用浏览器完成：注册/登录 → 创建三日行程 → 添加两个目的地 → 每日添加条目 → 键盘调整顺序 → 触发一次相邻时间成功和一次重叠失败 → 延长日期 → 尝试缩短有条目日期并被拒绝 → 状态迁移 → 刷新后数据一致。

浏览器测试账号、行程标题和备注统一带 `IT-TEST-#17-<batch>`；验收后通过 API 按精确 ID 清理，随后在 MySQL 回查五张表与账号相关测试数据均为 0。若清理受阻，立即在 `docs/runbooks/` 写入持久化待清理记录，不得报告完成。

### Step 5：主代理代码审查

逐项检查：

- `git diff origin/master...HEAD` 与 #17 规格一一对应，没有提前实现 #18–#32。
- 只有应用服务管理事务、版本和命令结果；Controller/Vue 不复制领域规则。
- 所有失败路径回滚；所有 ID 所属关系在数据库写入前验证。
- SQL 使用参数绑定；日志无令牌、备注、原始命令载荷；错误不泄露他人资源。
- 新增类、函数和字段都被真实调用，无死代码、重复 DTO 或第二事实源。

发现问题后先增加失败测试，再修复并重新运行完整验证。

### Step 6：提交文档与验收门禁

```powershell
git add CONTEXT.md docs/data/itinerary.md docs/adr/0003-itinerary-command-aggregate.md .github/workflows/ci.yml tests/scripts
git commit -m "docs: complete itinerary core data contract (#17)"
```

### Step 7：更新现有 PR，但不合并、不部署

在完整验证通过、秘密扫描为 0 后，将当前分支推送到现有 PR #33，更新 PR 描述中的 #17 状态和验证结果，保留“禁止合并/部署”的现有授权边界。等待远端 required checks 全部通过；任何失败均回到对应测试修复，不绕过门禁。

---

## 完成定义

#17 只有同时满足以下条件才可标记完成：

- 五张正式表迁移可在全新、重复和部分状态下收敛，初始化 SQL 与迁移一致。
- 所有者能通过真实登录链路创建、读取和编辑行程、目的地、日期、条目、顺序和状态。
- 幂等、乐观锁、权限、时间冲突、日期缩短、软删除与状态机在领域测试和真实 MySQL HTTP 测试中有证据。
- 前端逐动作自动保存、失败回滚、冲突恢复、键盘排序和刷新一致性通过测试及浏览器验收。
- 数据文档、ADR、领域词汇和 CI 已同步；无第二事实源、无测试数据残留、无秘密泄露。
- 当前分支与 PR checks 全绿；仍不合并、不部署。

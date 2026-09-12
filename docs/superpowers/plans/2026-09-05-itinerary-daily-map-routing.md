# 每日地图与路线规划实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为正式旅行行程交付可搜索和手工确认的地点引用、按日有序地图，以及可缓存、可部分降级的相邻路线参考。

**Architecture:** `itinerary` 模块继续拥有正式地点快照、命令、版本和 MySQL 事务；新增 `itinerarymap` 深模块通过公开应用接口读取最小化地图投影，并把高德 POI/路线响应转换为供应商无关模型。路线与搜索缓存只存 Redis，前端地图通过统一异步 Loader 渲染，所有外部失败都保留文字行程和手工地点路径。

**Tech Stack:** Java 17、Spring Boot、MyBatis、MySQL 8.4、Spring Data Redis、Spring `RestClient`、JUnit 5、Mockito、Testcontainers、Python `unittest`、Vue 3、Vitest、Ant Design Vue、高德 Web Service/JS API 2.0。

**Spec:** `docs/superpowers/specs/2026-09-05-itinerary-daily-map-routing-design.md`

## Global Constraints

- MySQL 是正式地点引用和旅行行程版本的唯一事实源；Redis 和前端状态必须可从 MySQL 重建。
- 高德原始 DTO 只能位于 `com.jiawa.lyw.itinerarymap.infrastructure.amap`，不得进入正式领域模型或数据库。
- 地点关联写操作必须复用 `commandId`、`expectedVersion`、负责人访问策略和现有行程事务语义。
- `itinerary_item.place_name` 不因地点关联、替换或移除而被静默修改。
- 每日地图逐段降级；路线失败不得删除、回滚或覆盖正式地点及行程安排。
- 后端 Web Service Key 与浏览器 JS Key 分离；真实 Key 不得进入仓库、日志、异常、测试快照或 URL 日志。
- 路线模式固定为 `WALKING`、`DRIVING`、`TRANSIT`；不自动替用户切换模式。
- 单日最多 50 个有效地点、49 段路线、每段最多 2,000 个折线点、最多 4 个并发供应商请求。
- 测试数据使用 `IT-TEST-#19` 标记，并在验收结束前精确删除和回查。
- 本计划在现有隔离 worktree `D:/idea/worktrees/20260830-intelligent-travel-platform/lyw` 内执行，不创建嵌套 worktree，不合并、不部署。

---

### Task 1: 建立正式地点表和可重复迁移

**Files:**
- Create: `sql/migrations/20260905_itinerary_daily_map.sql`
- Modify: `sql/travel_share.sql`
- Modify: `tests/scripts/migration_specs.py`
- Create: `tests/scripts/test_itinerary_daily_map_migration.py`
- Modify: `tests/scripts/test_itinerary_ci_contract.py`

**Interfaces:**
- Consumes: `MigrationSpec(name, apply_variable, schema_prefix)` 和 `itinerary`、`itinerary_item` 既有表。
- Produces: `ITINERARY_DAILY_MAP_MIGRATION`；MySQL 表 `itinerary_item_place`；应用变量 `apply_itinerary_daily_map_migration`。

- [ ] **Step 1: 写迁移契约失败测试**

```python
import unittest
from pathlib import Path

from tests.scripts.migration_specs import ITINERARY_DAILY_MAP_MIGRATION
from tests.scripts.migration_specs import ITINERARY_CORE_MIGRATION
from tests.scripts.mysql_migration_harness import run_sql_script, temporary_schema

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "sql" / "migrations" / "20260901_itinerary_core.sql"
MIGRATION_PATH = ROOT / "sql" / "migrations" / "20260905_itinerary_daily_map.sql"


class ItineraryDailyMapMigrationTests(unittest.TestCase):
    def install_core(self, mysql, schema):
        mysql.execute("CREATE TABLE member (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB;",
                      database=schema)
        run_sql_script(mysql, schema, CORE, ITINERARY_CORE_MIGRATION, apply=True)

    def test_dry_run_does_not_create_table(self):
        with temporary_schema(ITINERARY_DAILY_MAP_MIGRATION) as (mysql, schema):
            self.install_core(mysql, schema)
            report = run_sql_script(mysql, schema, MIGRATION_PATH,
                                    ITINERARY_DAILY_MAP_MIGRATION, apply=False)
            self.assertIn("missing_table_count\t1", report)

    def test_apply_converges_and_is_repeatable(self):
        with temporary_schema(ITINERARY_DAILY_MAP_MIGRATION) as (mysql, schema):
            self.install_core(mysql, schema)
            first = run_sql_script(mysql, schema, MIGRATION_PATH,
                                   ITINERARY_DAILY_MAP_MIGRATION, apply=True)
            second = run_sql_script(mysql, schema, MIGRATION_PATH,
                                    ITINERARY_DAILY_MAP_MIGRATION, apply=True)
            for report in (first, second):
                self.assertIn("missing_table_count\t0", report)
                self.assertIn("missing_column_count\t0", report)
                self.assertIn("missing_index_count\t0", report)
                self.assertIn("missing_foreign_key_count\t0", report)
                self.assertIn("legacy_column_count\t0", report)
```

- [ ] **Step 2: 运行迁移测试并确认 RED**

Run: `python -m unittest tests.scripts.test_itinerary_daily_map_migration -v`

Expected: FAIL，提示 `ITINERARY_DAILY_MAP_MIGRATION` 或迁移 SQL 尚不存在。

- [ ] **Step 3: 登记迁移并实现收敛 SQL**

在 `migration_specs.py` 增加：

```python
ITINERARY_DAILY_MAP_MIGRATION = MigrationSpec(
    name="itinerary_daily_map",
    apply_variable="apply_itinerary_daily_map_migration",
    schema_prefix="lyw_itinerary_daily_map_migration_test",
)
```

迁移用存储过程保护 apply，并创建规格中的 `itinerary_item_place`。文件末尾建立 expected tables/columns/indexes/foreign keys 临时表并输出六个固定指标：`missing_table_count`、`extra_table_count`、`missing_column_count`、`missing_index_count`、`missing_foreign_key_count`、`legacy_column_count`。旧字段扫描固定包含 `provider_json`、`amap_json`、`lng`、`lat`、`route_json`。

- [ ] **Step 4: 同步空库结构和 CI 契约**

把同一表定义加入 `sql/travel_share.sql` 的 itinerary 表组之后；在 `test_itinerary_ci_contract.py` 断言迁移文件已进入全量 Python discovery，并断言空库结构不存在高德原始字段。

- [ ] **Step 5: 验证 GREEN**

Run: `python -m unittest tests.scripts.test_itinerary_daily_map_migration tests.scripts.test_itinerary_ci_contract -v`

Expected: PASS；本地 MySQL 不可用时只有依赖实例的测试按项目既有规则 SKIP，纯契约测试必须 PASS。

- [ ] **Step 6: 提交迁移切片**

```powershell
git add -- sql/migrations/20260905_itinerary_daily_map.sql sql/travel_share.sql tests/scripts/migration_specs.py tests/scripts/test_itinerary_daily_map_migration.py tests/scripts/test_itinerary_ci_contract.py
git commit -m "feat: add itinerary place migration (#19)"
```

---

### Task 2: 将正式地点引用纳入行程聚合和命令边界

**Files:**
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/domain/ItineraryModels.java`
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/domain/ItineraryError.java`
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/application/ItineraryCommands.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerary/application/ItineraryPlaceApplicationService.java`
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/application/ItineraryRepository.java`
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/application/DefaultItineraryApplicationService.java`
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/infrastructure/ItineraryRows.java`
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/infrastructure/ItineraryMapper.java`
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/infrastructure/MyBatisItineraryRepository.java`
- Modify: `business/src/main/java/com/jiawa/lyw/itinerary/infrastructure/ItineraryConfiguration.java`
- Modify: `business/src/main/resources/mapper/itinerary/ItineraryMapper.xml`
- Modify: `business/src/test/java/com/jiawa/lyw/itinerary/application/ItineraryCommandContractTests.java`
- Modify: `business/src/test/java/com/jiawa/lyw/itinerary/infrastructure/ItineraryRepositoryIT.java`
- Modify: `business/src/test/java/com/jiawa/lyw/itinerary/api/ItineraryHttpIT.java`

**Interfaces:**
- Consumes: 现有 `ItineraryCommands.CommandEnvelope<T>`、`ItineraryCommands.CommandResult`、`ItineraryAccessPolicy` 和 `ItineraryRepository` 幂等存储。
- Produces: `ItineraryModels.PlaceSource`、`PlaceReference`、`MapItem`、`DayMapInput`；`PutItemPlace`、`RemoveItemPlace`；`ItineraryPlaceApplicationService`。

- [ ] **Step 1: 写领域值对象和命令失败测试**

```java
@Test
void validatesProviderAndManualPlaceIdentity() {
    assertDoesNotThrow(() -> new ItineraryCommands.PutItemPlace(
            ItineraryModels.PlaceSource.AMAP, "B000A", "故宫博物院", "景山前街4号",
            "110101", new BigDecimal("116.3970260"),
            new BigDecimal("39.9180580"), "GCJ02"));
    assertThrows(ItineraryException.class, () -> new ItineraryCommands.PutItemPlace(
            ItineraryModels.PlaceSource.MANUAL, "B000A", "手工地点", null,
            null, BigDecimal.ZERO, BigDecimal.ZERO, "GCJ02"));
    assertThrows(ItineraryException.class, () -> new ItineraryCommands.PutItemPlace(
            ItineraryModels.PlaceSource.AMAP, null, "故宫博物院", null,
            "110101", BigDecimal.ZERO, BigDecimal.ZERO, "GCJ02"));
}
```

- [ ] **Step 2: 运行命令测试并确认 RED**

Run: `mvn -pl business -Dtest=ItineraryCommandContractTests test`

Expected: compilation FAIL，缺少地点类型和命令。

- [ ] **Step 3: 实现供应商无关地点类型**

在 `ItineraryModels` 增加：

```java
public enum PlaceSource { AMAP, MANUAL }

public record PlaceReference(
        PlaceSource source, String sourcePlaceId, String name, String address,
        String administrativeAreaCode, BigDecimal longitude, BigDecimal latitude,
        String coordinateSystem,
        Instant confirmedAt, Instant updatedAt) {}

public record MapItem(
        long itemId, String title, LocalTime startTime, LocalTime endTime,
        String placeName, PlaceReference placeReference, long position) {}

public record DayMapInput(
        long itineraryId, long dayId, LocalDate date, List<MapItem> items) {
    public DayMapInput { items = List.copyOf(items); }
}
```

`ItineraryModels.Item` 增加末尾字段 `PlaceReference placeReference`；同步所有构造调用，禁止以 `null` 之外的伪默认地点填充历史数据。

- [ ] **Step 4: 实现地点命令及公开应用接口**

```java
public record PutItemPlace(
        ItineraryModels.PlaceSource source,
        String sourcePlaceId,
        String name,
        String address,
        String administrativeAreaCode,
        BigDecimal longitude,
        BigDecimal latitude,
        String coordinateSystem) {}

public record RemoveItemPlace() {}
```

构造器规范化文本并验证规格边界。`ItineraryPlaceApplicationService` 精确声明：

```java
ItineraryCommands.CommandResult putPlace(long actorMemberId, long itineraryId, long itemId,
        ItineraryCommands.CommandEnvelope<ItineraryCommands.PutItemPlace> command);
ItineraryCommands.CommandResult removePlace(long actorMemberId, long itineraryId, long itemId,
        ItineraryCommands.CommandEnvelope<ItineraryCommands.RemoveItemPlace> command);
ItineraryModels.DayMapInput dayMapInput(long actorMemberId, long itineraryId, long dayId);
```

- [ ] **Step 5: 写应用服务幂等、权限和版本失败测试**

在 `ItineraryCommandContractTests` 覆盖：首次关联版本 `7→8`；替换地点版本 `8→9`；移除版本 `9→10`；无引用移除仍完成一次命令；相同 `commandId` 重放不重复加版本；相同 ID 不同地点返回 `IDEMPOTENCY_CONFLICT`；旧版本返回 `VERSION_CONFLICT`；其他负责人返回 `ITINERARY_NOT_FOUND`；已软删除或跨行程 item 返回 `ITINERARY_ITEM_NOT_FOUND`。

- [ ] **Step 6: 实现同事务地点命令**

让 `DefaultItineraryApplicationService` 同时实现 `ItineraryApplicationService, ItineraryPlaceApplicationService`。新增操作常量：

```java
private static final String PUT_ITEM_PLACE_OPERATION = "PUT_ITEM_PLACE";
private static final String REMOVE_ITEM_PLACE_OPERATION = "REMOVE_ITEM_PLACE";
```

两个方法复用现有 reserve/replay/lock/version helper；锁定成功后验证当前非删除 item，`put` 调用 repository upsert，`remove` 调用 delete，随后 `bumpVersion` 和 `completeCommand`。任何失败由事务回滚，不保留命令占位或孤立地点。

- [ ] **Step 7: 实现 MyBatis 映射和最小地图读取投影**

Repository 新增：

```java
void upsertItemPlace(long itineraryId, long itemId, ItineraryCommands.PutItemPlace place, Instant now);
int deleteItemPlace(long itineraryId, long itemId);
Optional<ItineraryModels.DayMapInput> findDayMapInput(long itineraryId, long dayId);
```

`findItems` 左连接 `itinerary_item_place` 并映射 `PlaceReference`。`findDayMapInput` 使用行程、日、未删除安排项和地点表构造最小投影，顺序固定为 `(position, id)`，查询不读取 notes 或 estimated_cost。

- [ ] **Step 8: 用真实 MySQL 验证持久化和并发收敛**

在 `ItineraryRepositoryIT`/`ItineraryHttpIT` 增加 `IT-TEST-#19` 数据，验证保存重开、替换、移除、跨 item 防护、版本冲突回滚和两个并发相同命令只产生一个版本变化。测试 teardown 按 itinerary ID 删除地点、命令、items、days、destinations、itinerary 并回查计数为 0。

- [ ] **Step 9: 运行切片测试并提交**

Run: `mvn -pl business -Dtest=ItineraryCommandContractTests,ItineraryRepositoryIT,ItineraryHttpIT test`

Expected: PASS；需要容器的测试按现有 profile/环境运行。

```powershell
git add -- business/src/main/java/com/jiawa/lyw/itinerary business/src/main/resources/mapper/itinerary business/src/test/java/com/jiawa/lyw/itinerary
git commit -m "feat: persist confirmed itinerary places (#19)"
```

---

### Task 3: 实现地图领域、路线编排和 Redis 降级缓存

**Files:**
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/domain/ItineraryMapModels.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/domain/ItineraryMapError.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/domain/ItineraryMapException.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/application/PlaceSearchGateway.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/application/RoutePlanningGateway.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/application/RouteReferenceCache.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/application/ItineraryMapApplicationService.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/application/DefaultItineraryMapApplicationService.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/infrastructure/RedisRouteReferenceCache.java`
- Create: `business/src/test/java/com/jiawa/lyw/itinerarymap/domain/ItineraryMapModelsTests.java`
- Create: `business/src/test/java/com/jiawa/lyw/itinerarymap/application/ItineraryMapApplicationServiceTests.java`
- Create: `business/src/test/java/com/jiawa/lyw/itinerarymap/infrastructure/RedisRouteReferenceCacheIT.java`

**Interfaces:**
- Consumes: `ItineraryPlaceApplicationService.dayMapInput/putPlace/removePlace`。
- Produces: `PlaceSearchGateway.search`、`RoutePlanningGateway.plan`、`RouteReferenceCache` 和 `ItineraryMapApplicationService`。

- [ ] **Step 1: 写路线构造与相邻关系失败测试**

```java
@Test
void buildsLegsAcrossUnresolvedItemsWithoutLosingStops() {
    DailyMapView view = service.dailyMap(7L, 11L, 21L, RouteMode.WALKING);
    assertEquals(List.of(101L, 102L, 103L),
            view.stops().stream().map(Stop::itemId).toList());
    assertEquals(1, view.legs().size());
    assertEquals(101L, view.legs().get(0).fromItemId());
    assertEquals(103L, view.legs().get(0).toItemId());
    assertTrue(view.degraded());
}
```

- [ ] **Step 2: 运行应用测试并确认 RED**

Run: `mvn -pl business -Dtest=ItineraryMapModelsTests,ItineraryMapApplicationServiceTests test`

Expected: compilation FAIL，`itinerarymap` 类型尚不存在。

- [ ] **Step 3: 建立精确领域类型和端口**

`ItineraryMapModels` 声明：

```java
public enum RouteMode { WALKING, DRIVING, TRANSIT }
public enum RouteStatus { LIVE, CACHED, UNAVAILABLE }
public enum StopStatus { RESOLVED, UNRESOLVED }
public record Coordinate(BigDecimal longitude, BigDecimal latitude) {}
public record PlaceSearchQuery(String keyword, String city, int limit) {}
public record PlaceCandidate(PlaceSource source, String sourcePlaceId, String name,
        String address, String administrativeAreaCode, Coordinate coordinate,
        String coordinateSystem, String administrativeArea) {}
public record RouteValue(long distanceMeters, long durationSeconds,
        List<Coordinate> polyline, Instant calculatedAt) {}
public record CachedRoute(RouteValue route, Instant freshUntil) {}
public record Stop(int sequence, long itemId, String title, LocalTime startTime,
        LocalTime endTime, String placeName, PlaceReference placeReference,
        StopStatus status) {}
public record Leg(long fromItemId, long toItemId, RouteMode mode, RouteStatus status,
        Long distanceMeters, Long durationSeconds, List<Coordinate> polyline,
        Instant calculatedAt, boolean stale, String unavailableReason) {}
public record DailyMapView(long itineraryId, long dayId, LocalDate date, RouteMode mode,
        List<Stop> stops, List<Leg> legs, boolean degraded, Instant generatedAt) {}
```

所有 record 构造器做空值、范围和上限验证，并复制集合。

端口和应用服务精确签名为：

```java
public interface PlaceSearchGateway {
    List<PlaceCandidate> search(PlaceSearchQuery query);
}
public interface RoutePlanningGateway {
    RouteValue plan(Coordinate origin, Coordinate destination,
            RouteMode mode, String transitCityCode);
}
public interface RouteReferenceCache {
    Optional<CachedRoute> find(String key);
    void put(String key, CachedRoute route, Duration ttl);
}
public interface ItineraryMapApplicationService {
    List<PlaceCandidate> search(long actorMemberId, PlaceSearchQuery query);
    ItineraryCommands.CommandResult confirmPlace(long actorMemberId,
            long itineraryId, long itemId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.PutItemPlace> command);
    ItineraryCommands.CommandResult removePlace(long actorMemberId,
            long itineraryId, long itemId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.RemoveItemPlace> command);
    DailyMapView dailyMap(long actorMemberId, long itineraryId,
            long dayId, RouteMode mode);
}
```

- [ ] **Step 4: 写缓存状态机失败测试**

分别断言：新鲜缓存不调用 Gateway；陈旧缓存先尝试实时且成功返回 `LIVE`；实时失败返回 `CACHED/stale=true`；无缓存实时失败返回 `UNAVAILABLE`；Redis 读取异常按 miss 处理；Redis 写异常仍返回 `LIVE`；公交缺城市返回 `TRANSIT_CITY_REQUIRED`；50 个地点得到 49 段，51 个地点抛 `INVALID_MAP_REQUEST`。

- [ ] **Step 5: 实现确定性编排和并发上限**

`DefaultItineraryMapApplicationService` 接收 `Clock`、四个端口和专用 `Executor`。先在调用线程读取不可变 `DayMapInput`，再为每段建立带原序号的 `CompletableFuture<IndexedLeg>`；专用 executor 固定最多四线程。`allOf` 完成后按 index 排序，任何单 future 的可预期供应商错误在任务内部转换成降级 Leg，不让整批 exceptional completion。

缓存 key 用以下规范串的 SHA-256 小写十六进制：

```text
v1|AMAP|<MODE>|<origin-lng-6>,<origin-lat-6>|<destination-lng-6>,<destination-lat-6>|<transit-city-or-empty>
```

Redis key 固定 `itinerary-map:route:v1:<digest>`，值为 JSON `CachedRoute`，TTL 七天；`freshUntil=calculatedAt+24h`。

- [ ] **Step 6: 实现 Redis 适配器并做真实实例测试**

`RedisRouteReferenceCache` 使用独立 `StringRedisTemplate` key/value，不使用 Java 原生序列化。读取 JSON 畸形时删除精确 key 并按 miss；Redis 异常只记录稳定类别，不记录坐标或 key 全文。Testcontainers Redis 测试验证 TTL 在 `(6d23h, 7d]`、JSON 重开可读和畸形值清理。

- [ ] **Step 7: 运行测试并提交**

Run: `mvn -pl business -Dtest=ItineraryMapModelsTests,ItineraryMapApplicationServiceTests,RedisRouteReferenceCacheIT test`

Expected: PASS。

```powershell
git add -- business/src/main/java/com/jiawa/lyw/itinerarymap business/src/test/java/com/jiawa/lyw/itinerarymap
git commit -m "feat: orchestrate degradable itinerary routes (#19)"
```

---

### Task 4: 封装高德搜索与路线适配器

**Files:**
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/infrastructure/amap/AmapMapProperties.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/infrastructure/amap/dto/AmapResponseModels.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/infrastructure/amap/AmapPlaceSearchGateway.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/infrastructure/amap/AmapRoutePlanningGateway.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/infrastructure/ItineraryMapConfiguration.java`
- Modify: `business/src/main/resources/application-prod.properties`
- Modify: `business/src/main/resources/application.properties.example`
- Modify: `business/src/main/resources/application.yml.example`
- Modify: `.env.example`
- Create: `business/src/test/java/com/jiawa/lyw/itinerarymap/infrastructure/amap/AmapMapPropertiesTests.java`
- Create: `business/src/test/java/com/jiawa/lyw/itinerarymap/infrastructure/amap/AmapPlaceSearchGatewayTests.java`
- Create: `business/src/test/java/com/jiawa/lyw/itinerarymap/infrastructure/amap/AmapRoutePlanningGatewayTests.java`
- Modify: `business/src/test/java/com/jiawa/lyw/security/ProductionRuntimeSecretsTests.java`

**Interfaces:**
- Consumes: `PlaceSearchGateway`、`RoutePlanningGateway` 和 `ItineraryMapModels`。
- Produces: validated `app.map.amap.*` properties；高德 Web Service POI/route adapters；四线程 route executor。

- [ ] **Step 1: 写配置安全失败测试**

```java
@Test
void rejectsUnsafeProductionProperties() {
    assertThrows(IllegalArgumentException.class, () -> new AmapMapProperties(
            URI.create("https://restapi.amap.com?key=leak"), "change-me",
            Duration.ofSeconds(1), Duration.ofSeconds(3), 4, 512_000));
}

@Test
void redactsSecretFromToString() {
    AmapMapProperties value = validProperties("TEST-amap-web-service-key");
    assertFalse(value.toString().contains("TEST-amap-web-service-key"));
    assertTrue(value.toString().contains("<redacted>"));
}
```

- [ ] **Step 2: 运行适配器测试并确认 RED**

Run: `mvn -pl business -Dtest=AmapMapPropertiesTests,AmapPlaceSearchGatewayTests,AmapRoutePlanningGatewayTests test`

Expected: compilation FAIL，适配器尚不存在。

- [ ] **Step 3: 实现安全配置和有界 HTTP 客户端**

`AmapMapProperties` 使用 `@ConfigurationProperties(prefix="app.map.amap")`，验证 base URL 为无 user-info/query/fragment 的绝对 HTTP(S) URL，Key 长度 16–512 且不等于 `change-me`，超时为正且不超过 30 秒，并在 `toString()` 脱敏。

两个 Gateway 各自使用带 `SimpleClientHttpRequestFactory` 的 `RestClient`。请求通过 URI builder 编码参数；不得拼接或记录包含 Key 的完整 URL。响应使用 `InputStream` 有界读取，默认最大 512 KiB。一次重试使用 50–150ms 的受控抖动退避，测试注入固定 sleeper 和 jitter source，不能使用阻塞超过总调用预算的睡眠。

- [ ] **Step 4: 实现 POI 转换和失败分类**

调用 `/v3/place/text`，参数为 `keywords`、可选 `city`、`citylimit=true`、`offset`、`page=1`、`extensions=base`、`output=JSON`、`key`。只转换有合法 POI ID、名称和 `lng,lat` 的记录；全无合法项返回空列表。`status=0`、429、5xx、超时、畸形响应分别转换为稳定 `PROVIDER_UNAVAILABLE`、`PROVIDER_RATE_LIMITED`、`PROVIDER_TIMEOUT` 或 `INVALID_PROVIDER_RESPONSE`，不把 `info` 原文暴露给用户。

- [ ] **Step 5: 实现三种路线和折线抽稀**

按模式调用路线规划 2.0 对应 endpoint；请求起终点使用六位小数 GCJ-02。只读取首条可用路线，汇总 distance/duration，并把 step polyline 合并、去除相邻重复点。超过 2,000 点时用固定步长保留首尾点。公交缺城市由应用层提前降级，不发供应商请求。

- [ ] **Step 6: 覆盖有限重试和敏感日志**

使用本地 HTTP server/MockWebServer 顺序返回响应，验证连接错误、超时、429、5xx最多重试一次，其他 4xx、空结果、JSON 契约错误不重试；测试捕获日志并断言不包含 Key、完整 URL、关键词、地点名称和原始 body。

- [ ] **Step 7: 配置生产与测试事实源**

`application-prod.properties` 增加：

```properties
app.map.amap.base-url=${AMAP_WEB_SERVICE_BASE_URL:https://restapi.amap.com}
app.map.amap.api-key=${AMAP_WEB_SERVICE_KEY}
app.map.amap.connect-timeout=${AMAP_CONNECT_TIMEOUT:1s}
app.map.amap.response-timeout=${AMAP_RESPONSE_TIMEOUT:3s}
app.map.amap.route-concurrency=${AMAP_ROUTE_CONCURRENCY:4}
app.map.amap.max-response-bytes=524288
```

示例文件只使用 `change-me`；更新 `ProductionRuntimeSecretsTests` 断言 production 必须通过环境变量获取后端 Key，且不存在旧 `amap.key` 事实源。

- [ ] **Step 8: 运行测试并提交**

Run: `mvn -pl business -Dtest=AmapMapPropertiesTests,AmapPlaceSearchGatewayTests,AmapRoutePlanningGatewayTests,ProductionRuntimeSecretsTests test`

Expected: PASS。

```powershell
git add -- .env.example business/src/main/java/com/jiawa/lyw/itinerarymap/infrastructure business/src/main/resources/application-prod.properties business/src/main/resources/application.properties.example business/src/main/resources/application.yml.example business/src/test/java/com/jiawa/lyw/itinerarymap/infrastructure business/src/test/java/com/jiawa/lyw/security/ProductionRuntimeSecretsTests.java
git commit -m "feat: add bounded Amap adapters (#19)"
```

---

### Task 5: 交付地点与每日地图 HTTP 契约

**Files:**
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/api/ItineraryMapHttpModels.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/api/ItineraryMapController.java`
- Create: `business/src/main/java/com/jiawa/lyw/itinerarymap/api/ItineraryMapExceptionHandler.java`
- Create: `business/src/test/java/com/jiawa/lyw/itinerarymap/api/ItineraryMapControllerTests.java`
- Create: `business/src/test/java/com/jiawa/lyw/itinerarymap/api/ItineraryMapHttpIT.java`
- Modify: `business/src/test/java/com/jiawa/lyw/identity/api/IdentityRedisIT.java`
- Create: `business/src/test/java/com/jiawa/lyw/architecture/ItineraryMapModuleBoundaryTests.java`

**Interfaces:**
- Consumes: `ItineraryMapApplicationService`、`CurrentMemberProvider`、现有 `CommonResp`。
- Produces: 四个规格 HTTP 端点和稳定错误映射。

- [ ] **Step 1: 写 Controller 路由和错误失败测试**

```java
mockMvc.perform(get("/web/places/search")
        .param("keyword", "故宫").param("city", "110101").param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.items[0].source").value("AMAP"));

mockMvc.perform(get("/web/itineraries/11/days/21/map")
        .param("mode", "WALKING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.legs[0].status").value("CACHED"));
```

同时覆盖 PUT/DELETE JSON 命令、401、404 防枚举、400 输入错误、409 冲突、搜索 503 和每日地图 200 partial degradation。

- [ ] **Step 2: 运行 Controller 测试并确认 RED**

Run: `mvn -pl business -Dtest=ItineraryMapControllerTests test`

Expected: compilation FAIL，API 类型尚不存在。

- [ ] **Step 3: 实现请求响应 DTO 和安全转换**

DTO 使用字符串序列化所有 `id`/`*Id`，坐标保持 JSON decimal，时间使用 ISO 格式。PUT 请求通过：

```java
public record PutPlaceRequest(UUID commandId, long expectedVersion, PlaceInput place) {}
public record PlaceInput(String source, String sourcePlaceId, String name, String address,
        String administrativeAreaCode, BigDecimal longitude, BigDecimal latitude,
        String coordinateSystem) {}
public record RemovePlaceRequest(UUID commandId, long expectedVersion) {}
```

Controller 不解析供应商 JSON，不做第二套权限判断，只把当前成员 ID 和统一请求交给应用服务。

- [ ] **Step 4: 实现稳定异常映射**

映射规格中的 `INVALID_PLACE_QUERY`、`INVALID_PLACE`、`INVALID_ROUTE_MODE`、`INVALID_MAP_REQUEST`、`ITINERARY_NOT_FOUND`、`ITINERARY_ITEM_NOT_FOUND`、`ITINERARY_DAY_NOT_FOUND`、`VERSION_CONFLICT`、`IDEMPOTENCY_CONFLICT`、`MAP_PROVIDER_UNAVAILABLE`。响应 message 使用固定中文安全文本，不包含供应商 `info` 或异常 message 链。

- [ ] **Step 5: 增加真实 MySQL/Redis/HTTP 验收测试**

`ItineraryMapHttpIT` 启动真实 MySQL、Redis 和本地高德替身，覆盖：搜索→确认→重新 GET 行程；按顺序每日地图；三种模式；新鲜缓存不二次调用；陈旧缓存故障降级；无缓存故障为 `UNAVAILABLE`；手工地点在供应商全停时仍可保存；并发同命令只写一次；测试数据清理回查为零。

`IdentityRedisIT` 增加所有必需的 `TEST-` 地图配置，避免全应用启动因新属性缺失失败。

- [ ] **Step 6: 增加模块边界测试**

```java
noClasses().that().resideOutsideOfPackage("..itinerarymap.infrastructure.amap..")
        .should().dependOnClassesThat().resideInAPackage("..itinerarymap.infrastructure.amap..dto..");
noClasses().that().resideInAPackage("..itinerarymap..")
        .should().dependOnClassesThat().resideInAPackage("..itinerary.infrastructure..");
```

- [ ] **Step 7: 运行测试并提交**

Run: `mvn -pl business -Dtest=ItineraryMapControllerTests,ItineraryMapHttpIT,ItineraryMapModuleBoundaryTests,IdentityRedisIT test`

Expected: PASS；Docker 真集成由项目 runner 启动。

```powershell
git add -- business/src/main/java/com/jiawa/lyw/itinerarymap/api business/src/test/java/com/jiawa/lyw/itinerarymap/api business/src/test/java/com/jiawa/lyw/architecture/ItineraryMapModuleBoundaryTests.java business/src/test/java/com/jiawa/lyw/identity/api/IdentityRedisIT.java
git commit -m "feat: expose itinerary map HTTP workflow (#19)"
```

---

### Task 6: 扩展前端命令队列和地点选择体验

**Files:**
- Create: `web/src/modules/itinerary-map/itineraryMapHttp.js`
- Create: `web/src/modules/itinerary-map/itineraryMapHttp.test.js`
- Create: `web/src/modules/itinerary-map/PlacePickerDrawer.vue`
- Create: `web/src/modules/itinerary-map/PlacePickerDrawer.test.js`
- Modify: `web/src/modules/itinerary/itineraryEditor.js`
- Modify: `web/src/modules/itinerary/itineraryEditor.test.js`
- Modify: `web/src/modules/itinerary/ItineraryEditor.vue`
- Modify: `web/src/modules/itinerary/ItineraryEditorPage.test.js`
- Modify: `web/src/modules/itinerary/itinerary.css`

**Interfaces:**
- Consumes: HTTP endpoints from Task 5 and existing serial command queue.
- Produces: `itineraryMapHttp`、`PlacePickerDrawer`、editor `putPlace/removePlace` actions。

- [ ] **Step 1: 写 HTTP 适配器失败测试**

```javascript
expect(http.request).toHaveBeenCalledWith({
  method: 'put',
  url: '/lyw/web/itineraries/11/items/21/place',
  data: command,
})
expect(await api.dailyMap('11', '31', 'WALKING')).toMatchObject({ mode: 'WALKING' })
```

- [ ] **Step 2: 运行单文件测试并确认 RED**

Run: `npm test -- itineraryMapHttp.test.js`

Working directory: `web`

Expected: FAIL，模块不存在。

- [ ] **Step 3: 实现唯一地图 HTTP 适配器**

```javascript
export const createItineraryMapHttp = (http = request) => ({
  searchPlaces: ({ keyword, city, limit = 10 }) => send('get', '/lyw/web/places/search', undefined, { keyword, city, limit }),
  putPlace: (itineraryId, itemId, command) => send('put', `/lyw/web/itineraries/${itineraryId}/items/${itemId}/place`, command),
  removePlace: (itineraryId, itemId, command) => send('delete', `/lyw/web/itineraries/${itineraryId}/items/${itemId}/place`, command),
  dailyMap: (itineraryId, dayId, mode) => send('get', `/lyw/web/itineraries/${itineraryId}/days/${dayId}/map`, undefined, { mode }),
})
```

复用与 `itineraryHttp` 相同的 ID 字符串化和安全错误解析，不复制访问令牌逻辑。

- [ ] **Step 4: 写编辑器地点命令失败测试**

断言 `putPlace` 乐观更新 `item.placeReference`，成功更新版本；普通失败恢复旧引用；`removePlace` 不清空 `placeName`；409 停止队列并由 `reload()` 获取服务端快照；两个快速地点操作按当前 committed version 串行执行。

- [ ] **Step 5: 将地点操作接入既有命令队列**

在 `createItineraryEditor` 增加：

```javascript
putPlace: (itemId, place) => enqueue({
  payload: { place },
  apply: (snapshot) => { findItem(snapshot, itemId).item.placeReference = structuredClone(place) },
  invoke: (command) => mapApi.putPlace(itineraryId, String(itemId), command),
}),
removePlace: (itemId) => enqueue({
  payload: {},
  apply: (snapshot) => { findItem(snapshot, itemId).item.placeReference = null },
  invoke: (command) => mapApi.removePlace(itineraryId, String(itemId), command),
}),
```

调整构造函数注入 `mapApi`，测试和页面显式提供，不使用隐藏全局事实源。

- [ ] **Step 6: 写地点抽屉交互失败测试**

覆盖：trim 后空关键词不请求；搜索候选渲染；选择只预览不保存；点击确认 emit `confirm`；手工名称/坐标范围验证；服务失败保留关键词并显示手工入口；保存中禁用重复确认；关闭后焦点恢复和 `aria-live` 状态。

- [ ] **Step 7: 实现地点抽屉并嵌入行程编辑器**

`PlacePickerDrawer` 只管理查询、选择和手工表单，不直接修改 itinerary snapshot。`ItineraryEditor.vue` 在安排项显示引用名称/地址，保留 `placeName` 摘要，并调用 editor actions。普通失败显示可重试错误；版本冲突复用既有冲突卡片。

- [ ] **Step 8: 运行前端切片并提交**

Run: `npm test -- itineraryMapHttp.test.js PlacePickerDrawer.test.js itineraryEditor.test.js ItineraryEditorPage.test.js`

Working directory: `web`

Expected: PASS。

```powershell
git add -- web/src/modules/itinerary-map web/src/modules/itinerary
git commit -m "feat: add itinerary place picker (#19)"
```

---

### Task 7: 交付每日地图、路线卡片和安全 JS Loader

**Files:**
- Create: `web/src/modules/itinerary-map/amapLoader.js`
- Create: `web/src/modules/itinerary-map/amapLoader.test.js`
- Create: `web/src/modules/itinerary-map/DailyItineraryMap.vue`
- Create: `web/src/modules/itinerary-map/DailyItineraryMap.test.js`
- Create: `web/src/modules/itinerary-map/RouteLegList.vue`
- Create: `web/src/modules/itinerary-map/RouteLegList.test.js`
- Create: `web/src/modules/itinerary-map/itineraryMap.css`
- Create: `web/public/runtime-config.js`
- Modify: `web/src/modules/itinerary/ItineraryEditor.vue`
- Modify: `web/src/modules/itinerary/ItineraryEditorPage.test.js`
- Modify: `web/src/view/page/map.vue`
- Modify: `web/index.html`
- Modify: `.env.example`
- Modify: `deploy/compose.yaml`
- Modify: `deploy/frontend/Dockerfile`
- Move: `deploy/frontend/default.conf` to `deploy/frontend/default.conf.template`
- Create: `deploy/frontend/runtime-config.js.template`
- Create: `deploy/frontend/40-runtime-config.sh`
- Modify: `tests/scripts/test_deployment_compose_contract.py`

**Interfaces:**
- Consumes: `itineraryMapHttp.dailyMap` 与高德 JS API 2.0 Loader。
- Produces: 单例 `loadAmap()`；按日地图和文字路线卡片；无硬编码 Key 的生产配置链路。

- [ ] **Step 1: 写 Loader 安全失败测试**

```javascript
it('loads once and never embeds a key in index.html', async () => {
  window.__LYW_RUNTIME_CONFIG__ = { amapJsKey: 'TEST-js-key' }
  const first = loadAmap()
  const second = loadAmap()
  expect(first).toBe(second)
  expect(document.querySelectorAll('script[src*="webapi.amap.com"]').length).toBe(1)
})
```

Python/contract 测试读取 `web/index.html`，断言不存在 `key=`、`securityJsCode` 或 32 位十六进制高德 Key 字面量。

- [ ] **Step 2: 运行 Loader 和部署契约并确认 RED**

Run: `npm test -- amapLoader.test.js`

Working directory: `web`

Run: `python -m unittest tests.scripts.test_deployment_compose_contract -v`

Expected: 至少一项 FAIL，因为 HTML 仍同步硬编码 Key，Loader 尚不存在。

- [ ] **Step 3: 实现单例在线 Loader 和清理边界**

`loadAmap()` 优先读取 `window.__LYW_RUNTIME_CONFIG__.amapJsKey`，本地开发才回退 `import.meta.env.VITE_AMAP_JS_KEY`；验证非空值后把安全服务固定为同源 `/_AMapService/`，异步加载官方 `loader.js` 并请求 JS API `2.0` 与 `AMap.ToolBar`。相同配置共享 promise，不同配置在已加载后拒绝，失败清除 promise 以允许一次用户重试。组件 `onBeforeUnmount` 调用 `map.destroy()` 并清除 marker/polyline 引用。

- [ ] **Step 4: 写每日地图可观察行为失败测试**

覆盖：展开后才请求；Marker 1..N 与 stop 顺序一致；路线模式切换重新请求；LIVE/CACHED/UNAVAILABLE 和 stale 文案；未解析地点仍在列表；API partial degradation 显示但不清空成功路段；Loader reject 后仍显示地点和文字路线；单点居中、多点 fit view；卸载 destroy。

- [ ] **Step 5: 实现地图与路线卡片**

`DailyItineraryMap` 接收 `itineraryId`、`dayId`，内部 mode 默认 `WALKING`。地图只负责绘制 resolved stops 和可用 leg polyline；`RouteLegList` 始终以可访问文字渲染 stop/leg。距离格式小于 1000m 显示整数米，否则一位小数公里；时长小于 60 分钟显示分钟，否则显示小时和分钟。

- [ ] **Step 6: 接入编辑器并迁移旧社区地图**

每个 day section 增加折叠按钮和 `DailyItineraryMap`。`web/src/view/page/map.vue` 改用同一 `loadAmap()`，但继续读取旧 `/lyw/web/location/*` 社区接口。移除 `web/index.html` 的高德 `<script ...key=...>`，不改变社区地点事实源。

- [ ] **Step 7: 打通运行时配置**

前端容器消费 `AMAP_JS_KEY` 和 `AMAP_JS_SECURITY_CODE`。`40-runtime-config.sh` 只把 `AMAP_JS_KEY` 经 JSON 安全转义后写到 `/usr/share/nginx/html/travel/runtime-config.js`；安全密钥只能由 `default.conf.template` 的 `/_AMapService/` 同源代理追加到上游。Dockerfile 用 `COPY --chmod=755` 安装脚本，并把 Nginx 配置放入 `/etc/nginx/templates/default.conf.template`；Compose 为前端挂载现有 secrets env file，并设置 `NGINX_ENVSUBST_FILTER=^AMAP_JS_SECURITY_CODE$`，防止替换 Nginx 自身变量。后端仍只消费 `AMAP_WEB_SERVICE_KEY`。

部署契约断言：Web Service Key 不进入前端；`AMAP_JS_SECURITY_CODE` 不进入 `runtime-config.js`、Web dist 或日志；`web/public/runtime-config.js` 默认只设置空对象；生产容器启动后公开配置只含 `amapJsKey`；旧硬编码 Key 不存在。开发模式可从 `VITE_AMAP_JS_KEY`/`VITE_AMAP_JS_SECURITY_CODE` 读取明确的本机值，但 `.env.example` 仍只给出 `change-me`。

- [ ] **Step 8: 运行前端与部署切片并提交**

Run: `npm test -- amapLoader.test.js DailyItineraryMap.test.js RouteLegList.test.js ItineraryEditorPage.test.js`

Working directory: `web`

Run: `npm run build`

Working directory: `web`

Run: `python -m unittest tests.scripts.test_deployment_compose_contract -v`

Expected: PASS；构建无 Key 字面量扫描命中。

```powershell
git add -- .env.example deploy/compose.yaml deploy/frontend web/index.html web/public/runtime-config.js web/src/modules/itinerary-map web/src/modules/itinerary/ItineraryEditor.vue web/src/modules/itinerary/ItineraryEditorPage.test.js web/src/view/page/map.vue tests/scripts/test_deployment_compose_contract.py
git commit -m "feat: render daily itinerary routes (#19)"
```

---

### Task 8: 收敛领域文档、完整验证和票据证据

**Files:**
- Modify: `CONTEXT.md`
- Create: `docs/data/itinerary-map.md`
- Modify: `docs/data/itinerary.md`
- Modify: `README.md`
- Modify: `tests/scripts/test_itinerary_ci_contract.py`
- Modify: `scripts/run_backend_integration.py`

**Interfaces:**
- Consumes: Tasks 1–7 的正式事实、配置和测试。
- Produces: 单一领域语言、数据链路、运行说明、CI 真集成清单和 #19 验收证据。

- [ ] **Step 1: 写文档与 CI 防回流失败测试**

在 `test_itinerary_ci_contract.py` 断言：

```python
self.assertIn("正式地点引用", context)
self.assertIn("路线参考", context)
self.assertNotIn("key=fe5fe3e22a225340ed32d789c42610c2", web_index)
self.assertIn("ItineraryMapHttpIT", integration_runner)
self.assertIn("20260905_itinerary_daily_map.sql", migration_inventory)
```

再断言生产配置只消费 `AMAP_WEB_SERVICE_KEY`，旧 `amap.key` 不存在，新模块外没有高德 DTO 引用。

- [ ] **Step 2: 运行契约测试并确认 RED**

Run: `python -m unittest tests.scripts.test_itinerary_ci_contract -v`

Expected: FAIL，领域词汇和数据文档尚未同步。

- [ ] **Step 3: 更新唯一事实文档**

`CONTEXT.md` 增加：

```text
正式地点引用：用户确认后与一个行程安排项关联的供应商无关地点快照；描述计划地点，不代表实际到访。
路线参考：相邻正式地点引用在指定出行方式下的可失效距离、预计时长和折线；不是正式行程或执行事实。
```

`docs/data/itinerary-map.md` 记录表字段/约束、HTTP 读写、Gateway 转换、Redis key/TTL、失败降级、配置归属、测试数据清理和旧社区地图边界。`docs/data/itinerary.md` 只增加指向该正式扩展文档的链接，不复制字段枚举。README 增加本地安全配置和不带真实 Key 的运行说明。

- [ ] **Step 4: 将地图集成测试登记到统一 runner**

扩展 `scripts/run_backend_integration.py`，让 `--containers` 包含 `ItineraryMapHttpIT` 与 Redis 缓存 IT，并继续由测试自己创建隔离容器；不得读取宿主 `SPRING_DATASOURCE_URL` 或 `SPRING_DATA_REDIS_URL`。

- [ ] **Step 5: 运行目标测试组**

Run: `mvn -pl business -Dtest='com.jiawa.lyw.itinerary.**,com.jiawa.lyw.itinerarymap.**,com.jiawa.lyw.architecture.Itinerary*' test`

Run: `npm test -- itineraryMapHttp.test.js PlacePickerDrawer.test.js amapLoader.test.js DailyItineraryMap.test.js RouteLegList.test.js itineraryEditor.test.js ItineraryEditorPage.test.js`

Working directory for npm: `web`

Run: `python -m unittest tests.scripts.test_itinerary_daily_map_migration tests.scripts.test_itinerary_ci_contract tests.scripts.test_deployment_compose_contract -v`

Expected: all PASS。

- [ ] **Step 6: 运行完整本地门禁**

```powershell
python -m unittest discover -s tests -t . -v
mvn --batch-mode --no-transfer-progress test
Push-Location web; npm test; npm run build; Pop-Location
Push-Location admin; npm run build; Pop-Location
mvn --batch-mode --no-transfer-progress -Pdeployment clean package
python scripts/security/scan_repository.py .
python scripts/security/scan_repository.py --all-refs .
docker compose -f deploy/compose.yaml config --quiet
python -m scripts.run_backend_integration --containers
git diff --check
```

Expected: 新增与既有测试全部 PASS；若 `--all-refs` 仅命中已登记的历史凭证，则保持“禁止合并”安全门禁并记录该外部轮换事项，不能把它改写为 PASS。

- [ ] **Step 7: 执行双轴代码审查**

使用 `/code-review`，固定点为 `3dc210a` 的父提交或 #19 开始前的已确认 commit。Standards 轴核对 AGENTS、模块边界、唯一事实源、秘密与测试数据；Spec 轴逐条映射 #19 四项验收。项目规则未明确授权子代理时，由主代理本地完成两轴并在记录中说明限制，不擅自创建代理。

- [ ] **Step 8: 修复审查发现并重新运行受影响门禁**

对每一项可操作发现先写回归测试，再修复并运行目标测试；任何安全高危保持“禁止合并”。审查无发现时记录“未发现可操作问题”，不得省略验证证据。

- [ ] **Step 9: 提交 #19 收尾**

```powershell
git add -- CONTEXT.md README.md docs/data/itinerary-map.md docs/data/itinerary.md scripts/run_backend_integration.py tests/scripts/test_itinerary_ci_contract.py
git commit -m "docs: record itinerary map data flow (#19)"
git status --short --branch
```

- [ ] **Step 10: 推送并验证 PR（已获当前功能分支推送授权）**

```powershell
git push origin codex/20260829-intelligent-travel-platform
gh pr checks 33 --watch --interval 10
gh pr view 33 --json headRefOid,baseRefName,mergeable,reviewDecision,statusCheckRollup,url
```

Expected: PR head 与本地 HEAD 一致，五类 CI job 全部 PASS。不得合并或部署；历史凭证未完成外部轮换前继续报告“禁止合并”。

# 每日地图与路线规划设计

## 状态

已批准设计。对应 GitHub Issue #19，父规格为 #12，前置能力为 #17 的手工旅行行程核心。本设计只交付经过确认的行程地点引用、每日地图和相邻安排项路线参考；酒店、火车和航班参考由 #20 交付，成员权限由 #21 扩展，途中实际路线由 #23 单独记录。

## 目标

让旅行者在编辑正式旅行行程时搜索并确认地点，按每日安排顺序查看空间位置和相邻路线、距离与预计时长。地图供应商故障、超时、限流、无结果或缓存不可用都不得改变、删除或阻塞既有正式行程；用户始终可以保存手工地点并继续规划。

## 决策摘要

- 新增 `itinerarymap` 深模块，负责地点搜索、地图日视图、路线编排、供应商适配和派生缓存。
- 经用户确认的供应商无关地点快照属于 `itinerary` 聚合扩展，保存在 MySQL；高德原始请求、响应和路线结果不成为正式业务事实。
- 地点关联、替换和移除复用现有行程命令、幂等、负责人访问策略、事务与乐观锁，并使行程版本递增。
- 路线只在读取每日地图时按相邻有效地点计算。Redis 保存可失效的派生缓存；缓存可全部删除并从 MySQL 地点事实重新生成。
- 第一版支持 `WALKING`、`DRIVING`、`TRANSIT` 三种路线模式，不预建多供应商编排、离线路网或真实导航系统。
- 高德 Web Service Key 只在后端注入；浏览器地图使用单独的 Web 端 JS Key。移除仓库中现有硬编码 JS Key，生产环境使用高德支持的代理安全模式。
- 旧社区“目的地地图”仍展示帖子地点，不成为行程地点的事实源；它只与新行程地图复用安全的 JS API Loader 和纯展示组件。

## 正式领域语言

### 正式地点引用

**正式地点引用**是用户确认后与一个未删除行程安排项关联的供应商无关地点快照。它描述计划中的地点，不证明用户实际到访，也不是帖子地点或行程目的地。

一个正式地点引用包含：

- `itinerary_id`、`item_id`：所属旅行行程与安排项；一个安排项最多一个当前引用。
- `source`：`AMAP` 或 `MANUAL`。
- `source_place_id`：供应商地点 ID；`AMAP` 必填，`MANUAL` 必须为空。
- `name`：1–200 个 Unicode 字符。
- `address`：可为空，非空时最多 500 个字符。
- `administrative_area_code`：可为空；非空时为高德返回的六位 adcode，用于公交路线城市上下文。
- `longitude`：`DECIMAL(10,7)`，范围 `[-180, 180]`。
- `latitude`：`DECIMAL(10,7)`，范围 `[-90, 90]`。
- `coordinate_system`：本阶段固定为 `GCJ02`。
- `confirmed_at`、`updated_at`：UTC 时间戳。

供应商候选只有在用户执行确认命令后才成为正式地点引用。前端搜索结果、Redis 搜索缓存、高德 POI 类型、评分、照片、营业信息和原始 JSON 都不是正式事实。

原有 `itinerary_item.place_name` 保持人工可读文本的既有语义。关联地点时不得静默覆盖它；读取接口同时返回 `placeName` 和 `placeReference`，由界面优先显示引用名称并保留人工文本。删除正式地点引用不清空 `place_name`。

### 路线参考

**路线参考**是两个相邻正式地点引用在指定路线模式下的派生结果，包括距离、预计时长和用于绘图的折线。它会随路网、供应商算法和时间变化，不是旅行行程或实际执行事实。

每一段路线具有：

- `fromItemId`、`toItemId`：相邻安排项。
- `mode`：`WALKING`、`DRIVING` 或 `TRANSIT`。
- `status`：`LIVE`、`CACHED` 或 `UNAVAILABLE`。
- `distanceMeters`、`durationSeconds`：只有可用时返回非负整数。
- `polyline`：按顺序的 GCJ-02 坐标点；不可用时为空列表。
- `calculatedAt`：供应商成功计算时间；不可用时为空。
- `stale`：是否超过新鲜期但仍在允许的故障降级期。

相邻关系只在当日未删除安排项中按 `(position, id)` 顺序建立，并跳过没有正式地点引用的安排项。例如 A 有地点、B 无地点、C 有地点时，A 与 C 形成一段路线，响应同时明确 B 的地点状态为 `UNRESOLVED`。

## 模块边界

### `itinerary` 模块扩展

`itinerary` 继续拥有正式聚合、写命令和 MySQL 事实。新增公开应用边界：

```java
public interface ItineraryPlaceApplicationService {
    ItineraryCommands.CommandResult putPlace(
            long actorMemberId,
            long itineraryId,
            long itemId,
            ItineraryCommands.CommandEnvelope<PutPlace> command);

    ItineraryCommands.CommandResult removePlace(
            long actorMemberId,
            long itineraryId,
            long itemId,
            ItineraryCommands.CommandEnvelope<RemovePlace> command);

    DayMapInput dayMapInput(long actorMemberId, long itineraryId, long dayId);
}
```

`PutPlace` 只包含已经规范化的供应商无关字段；领域规则再次验证来源、长度、坐标范围和坐标系。`dayMapInput` 只暴露地图需要的行程 ID、日 ID、日期、有序安排项 ID/标题/时间、人工地点名与正式地点引用，不暴露私人备注、预算或其他成员信息。

`putPlace` 与 `removePlace` 使用操作名 `PUT_ITEM_PLACE` 和 `REMOVE_ITEM_PLACE` 写入现有 `itinerary_command`。相同 `commandId`、成员、操作和规范化请求摘要返回原结果；不同请求返回 `IDEMPOTENCY_CONFLICT`。命令锁定行程、校验负责人、`expectedVersion` 和未删除安排项后，在同一事务内更新地点并把版本加一。

### `itinerarymap` 模块

模块外只使用以下应用接口，不得依赖内部 Gateway、Mapper、缓存键或高德 DTO：

```java
public interface ItineraryMapApplicationService {
    PlaceSearchResult search(long actorMemberId, PlaceSearchQuery query);
    ItineraryCommands.CommandResult confirmPlace(
            long actorMemberId, long itineraryId, long itemId, ConfirmPlaceCommand command);
    DailyMapView dailyMap(long actorMemberId, long itineraryId, long dayId, RouteMode mode);
}
```

内部组件职责：

- `PlaceSearchGateway`：供应商无关的 POI 搜索端口。
- `RoutePlanningGateway`：供应商无关的单段路线端口。
- `AmapPlaceSearchGateway`：调用高德 POI 关键词搜索并转换成 `PlaceCandidate`。
- `AmapRoutePlanningGateway`：调用高德路线规划 2.0 并只选择首条可用方案，转换为统一路线。
- `RouteReferenceCache`：读写派生路线缓存；Redis 实现失败时表现为缓存未命中，不中断实时调用。
- `DefaultItineraryMapApplicationService`：编排搜索、确认、相邻路段、缓存和部分降级。

高德 DTO 只存在于 `itinerarymap.infrastructure.amap`。架构测试禁止其他模块依赖该包，也禁止 `itinerarymap` 依赖 `itinerary.infrastructure`。

## 数据模型与迁移

新增 `itinerary_item_place`：

```sql
CREATE TABLE itinerary_item_place (
    item_id BIGINT NOT NULL,
    itinerary_id BIGINT NOT NULL,
    source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_place_id VARCHAR(128) NULL,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(500) NULL,
    administrative_area_code CHAR(6) CHARACTER SET ascii COLLATE ascii_bin NULL,
    longitude DECIMAL(10,7) NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    coordinate_system VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    confirmed_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (item_id),
    INDEX idx_itinerary_item_place_itinerary (itinerary_id, item_id),
    CONSTRAINT fk_itinerary_item_place_item FOREIGN KEY (item_id)
        REFERENCES itinerary_item (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_itinerary_item_place_itinerary FOREIGN KEY (itinerary_id)
        REFERENCES itinerary (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_itinerary_item_place_source CHECK (source IN ('AMAP', 'MANUAL')),
    CONSTRAINT chk_itinerary_item_place_source_id CHECK (
        (source = 'AMAP' AND source_place_id IS NOT NULL)
        OR (source = 'MANUAL' AND source_place_id IS NULL)
    ),
    CONSTRAINT chk_itinerary_item_place_area_code CHECK (
        administrative_area_code IS NULL OR administrative_area_code REGEXP '^[0-9]{6}$'
    ),
    CONSTRAINT chk_itinerary_item_place_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_itinerary_item_place_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_itinerary_item_place_coordinate_system CHECK (coordinate_system = 'GCJ02')
);
```

应用层还必须验证 `item_id` 确实属于同一个 `itinerary_id`，不能只依赖两个独立外键。软删除安排项时保留地点行用于审计引用，但所有当前查询排除已软删除项；若以后复制行程，由复制接口显式复制当前地点引用并生成新的安排项关系。

空库结构同步更新 `sql/travel_share.sql`。升级脚本新增 `sql/migrations/20260905_itinerary_daily_map.sql`，复用 `MigrationSpec`，默认 dry-run，显式变量才 apply，并提供缺失表、列、索引、外键、约束和旧命名残留统计。迁移可重复执行并能从只创建了部分结构的状态收敛，不生成业务地点或路线缓存。

数据文档新增 `docs/data/itinerary-map.md`，记录正式字段、派生缓存、读写链路、配置、清理方式和与旧社区地点的兼容边界。`CONTEXT.md` 增加“正式地点引用”和“路线参考”，不把“行程目的地”“帖子地点”混为同义词。

## 地点搜索与确认

搜索请求规则：

- `keyword`：去除首尾空白后 1–80 个字符。
- `city`：可为空；非空时为 1–80 个字符，可接受城市名、citycode 或 adcode。
- `limit`：默认 10，范围 1–20。
- 用户必须已认证，但搜索不要求指定行程，避免为每次输入联想读取聚合。

高德适配器使用 Web Service POI 关键词搜索，只请求页面需要的字段。统一候选返回 `source`、`sourcePlaceId`、`name`、`address`、`administrativeAreaCode`、`longitude`、`latitude`、`coordinateSystem` 和可为空的行政区摘要，不返回 Key、原始 `info`、供应商调用栈或完整 JSON。

确认命令提交完整统一候选或手工地点。服务端不把前端标记的 `source` 当作权限证据，而是按同一领域规则验证全部字段；允许用户调整自己行程中的地点坐标，因为 `MANUAL` 本来就是正式支持的输入。`AMAP` 必须携带外部 ID，`MANUAL` 禁止伪造外部 ID。

搜索可使用 10 分钟 Redis 缓存，键只包含规范化关键词、城市和限制值的 SHA-256 摘要，值为统一候选，不包含用户 ID 或访问令牌。缓存仅减少供应商请求，不参与确认命令的正确性。

## 每日地图与路线编排

读取流程：

1. 通过 `ItineraryPlaceApplicationService.dayMapInput` 执行对象级访问控制并读取当日正式地点。
2. 按行程顺序形成地图 stop；没有引用的安排项仍返回为 `UNRESOLVED`。
3. 从具有引用的相邻 stop 形成 route leg。
4. 对每段路线先读取 Redis。新鲜缓存直接返回 `CACHED`。
5. 缓存未命中或已过新鲜期时调用供应商。成功结果返回 `LIVE` 并写缓存。
6. 供应商失败时，存在七天降级期内旧缓存则返回 `CACHED`、`stale=true`；否则返回 `UNAVAILABLE`。
7. 任意一段失败不取消其他路段，最终响应以 `degraded` 表明是否存在未解析地点、陈旧缓存或不可用路线。

缓存键由版本、供应商、路线模式、起终点坐标（统一舍入到小数点后六位）及公交所需城市 adcode 的规范字符串摘要组成，不包含地点名称、行程 ID、成员 ID 或私人内容。缓存值带 `calculatedAt` 和 `freshUntil`，Redis TTL 为七天；24 小时后只允许在供应商故障时作为陈旧降级结果，不能在供应商健康时阻止刷新。

公交路线要求城市上下文。若起终点不能确定同一支持城市，单段返回 `UNAVAILABLE` 和稳定原因 `TRANSIT_CITY_REQUIRED`，不自动改用驾车或步行，避免界面声称了用户未选择的方式。

路线折线设置响应上限：单段最多 2,000 个坐标点，超过后使用保持首尾点的确定性抽稀；当日最多计算 49 段路线，对应最多 50 个有地点的安排项。超限返回 `INVALID_MAP_REQUEST`，防止单次读取放大供应商调用和前端渲染成本。

## HTTP 契约

所有端点沿用 Bearer 身份与 `CommonResp` 外壳。

### 搜索地点

`GET /web/places/search?keyword=&city=&limit=`

成功返回：

```json
{
  "items": [
    {
      "source": "AMAP",
      "sourcePlaceId": "opaque-provider-id",
      "name": "故宫博物院",
      "address": "景山前街4号",
      "administrativeAreaCode": "110101",
      "longitude": 116.397026,
      "latitude": 39.918058,
      "coordinateSystem": "GCJ02",
      "administrativeArea": "北京市东城区"
    }
  ]
}
```

### 确认或替换地点

`PUT /web/itineraries/{itineraryId}/items/{itemId}/place`

```json
{
  "commandId": "UUID",
  "expectedVersion": 7,
  "place": {
    "source": "AMAP",
    "sourcePlaceId": "opaque-provider-id",
    "name": "故宫博物院",
    "address": "景山前街4号",
    "administrativeAreaCode": "110101",
    "longitude": 116.397026,
    "latitude": 39.918058,
    "coordinateSystem": "GCJ02"
  }
}
```

成功返回 `itineraryId`、`itemId` 和新 `version`。同一个端点用于首次关联与替换。

### 移除地点

`DELETE /web/itineraries/{itineraryId}/items/{itemId}/place`

请求正文只包含 `commandId` 和 `expectedVersion`。重复相同命令返回第一次的成功结果；不同命令在已经没有引用时也作为合法幂等业务结果完成并递增一次版本。

### 每日地图

`GET /web/itineraries/{itineraryId}/days/{dayId}/map?mode=WALKING`

返回 `itineraryId`、`dayId`、`date`、`mode`、`stops`、`legs`、`degraded` 和 `generatedAt`。Stop 顺序与行程安排顺序一致，包含 `sequence`、`itemId`、标题、时间、人工地点名、正式地点引用和 `RESOLVED`/`UNRESOLVED` 状态。Leg 使用前述路线参考契约。

## 错误与降级语义

- 400 `INVALID_PLACE_QUERY`：关键词、城市或数量无效。
- 400 `INVALID_PLACE`：确认字段、来源、坐标或坐标系无效。
- 400 `INVALID_ROUTE_MODE`：路线模式不受支持。
- 400 `INVALID_MAP_REQUEST`：当日可计算路段超过上限。
- 401：沿用身份模块未认证响应。
- 404 `ITINERARY_NOT_FOUND`：行程不存在或无读取权限。
- 404 `ITINERARY_ITEM_NOT_FOUND`：在有行程权限后，安排项不存在、已删除或不属于该行程。
- 404 `ITINERARY_DAY_NOT_FOUND`：行程日不存在或不属于该行程。
- 409 `VERSION_CONFLICT`、`IDEMPOTENCY_CONFLICT`：沿用行程命令语义。
- 503 `MAP_PROVIDER_UNAVAILABLE`：地点搜索没有缓存且供应商不可用。

每日地图路线失败不使用 503；端点返回 200 和逐段状态。日志只记录供应商、操作、内部相关 ID、耗时、HTTP 类别和稳定错误分类，禁止记录 Key、Authorization、完整请求 URL、用户关键词、私人地点名称或供应商原始响应。

外部调用连接超时 1 秒、整体响应超时 3 秒。只对 GET 搜索或路线请求的连接错误、超时、429 和 5xx 最多重试一次；确定性 4xx、响应契约错误和空结果不重试。重试使用短抖动退避，单段总调用预算不得超过 7 秒。每日多段路线限制最多 4 个并发供应商请求，避免串行放大延迟和触发限流。

## 高德适配与配置

后端使用当前官方 Web Service 能力：POI 关键词搜索与路线规划 2.0。供应商 URL、状态码、字段名、策略值和折线语法全部封装在适配器内；领域接口不出现 `pois`、`paths`、`steps`、`infoCode` 等高德命名。

配置：

- `AMAP_WEB_SERVICE_BASE_URL`：生产默认 `https://restapi.amap.com`，测试指向本地替身。
- `AMAP_WEB_SERVICE_KEY`：必填后端秘密，只由环境变量注入。
- `AMAP_CONNECT_TIMEOUT`：生产默认 `1s`。
- `AMAP_RESPONSE_TIMEOUT`：生产默认 `3s`。
- `AMAP_ROUTE_CONCURRENCY`：生产默认 `4`。
- `AMAP_JS_KEY`：前端容器运行时注入的 Web 端 JS API Key；容器启动脚本只把这一公开客户端标识写入 `runtime-config.js`，不与 Web Service Key 共用。
- `AMAP_JS_SECURITY_CODE`：只注入前端 Nginx 容器，由同源 `/_AMapService/` 代理追加到上游请求；不得写入 `runtime-config.js` 或浏览器产物。

本地开发允许使用 `VITE_AMAP_JS_KEY` 和 `VITE_AMAP_JS_SECURITY_CODE` 作为明确标注的非生产回退；生产构建不消费这两个值。前端 `amapLoader` 优先读取 `window.__LYW_RUNTIME_CONFIG__.amapJsKey`，并把安全服务固定为同源 `/_AMapService/`。Nginx 配置由官方镜像启动时只对 `AMAP_JS_SECURITY_CODE` 执行 envsubst，代理访问日志关闭，响应和错误页不得回显上游 URL 或安全密钥。

配置校验在生产启动时拒绝缺失或占位值。测试使用明确带 `TEST-` 前缀的占位配置。`.env.example` 只记录变量名与 `change-me`，不得包含真实值。

官方参考：

- [高德 POI 搜索](https://lbs.amap.com/api/webservice/guide/api/search/)
- [高德路线规划 2.0](https://lbs.amap.com/api/webservice/guide/api/newroute)
- [高德 JS API 2.0 Loader](https://lbs.amap.com/api/javascript-api-v2/guide/abc/load)

## 前端体验

行程编辑器在每个安排项中增加地点状态：未确认时显示“设置地点”，已确认时显示地点名称、地址摘要以及“更换”“移除”。点击后打开地点抽屉：

1. 默认页签提供关键词和可选城市搜索。
2. 选择结果后展示名称、地址和地图位置预览，用户点击“确认到此安排”才提交命令。
3. “手工地点”页签允许输入名称、可选地址，并在地图上点选 GCJ-02 坐标；地图不可用时允许输入经纬度。
4. 保存期间显示“正在关联地点”；版本冲突复用现有编辑器冲突重载，不自动覆盖或重放。
5. 搜索不可用时保留已输入关键词，展示安全错误和手工入口。

每个行程日增加可折叠的“每日地图”：

- Marker 使用从 1 开始的顺序号，与安排项顺序一致。
- 已解析地点自动调整视野；一个地点时居中，多个地点时适配边界。
- 可选择步行、驾车或公交，切换只刷新路线读取，不修改正式行程。
- 路段卡片显示起终点、模式、距离、预计时长和实时/缓存/暂不可用状态。
- `UNRESOLVED` 安排项显示“请先设置地点”，不会从文字行程中消失。
- 地图脚本失败时保留有序地点列表和可用的文字路线卡片，不显示空白无解释区域。

新增 `amapLoader` 作为唯一浏览器加载入口，使用官方 JS API Loader 2.0。`web/index.html` 只加载无秘密的同源 `runtime-config.js`，不再同步加载带 Key 的高德 URL。Loader 失败可重试一次，并在组件卸载时清理地图实例、Marker 和 Polyline，防止路由切换泄漏。

## 安全、隐私与可访问性

- Web Service Key 不返回浏览器，不出现在 URL 日志、异常消息、测试快照或 Git 历史新增内容中。
- JS Key 与后端 Key 分离，并配置域名白名单；生产安全密钥通过代理安全服务使用。
- 所有行程和地图日读取在后端执行对象级访问控制，不能依赖前端隐藏按钮。
- 搜索候选和路线缓存不包含成员身份、私人备注、费用或访问令牌。
- 地点抽屉具有可读标题、焦点圈定与关闭后焦点恢复；搜索结果和路线降级通过 `aria-live` 通知。
- 地图不是唯一信息载体；Marker 顺序、地点名称、距离和时间同时以文字列表提供。
- 手工坐标输入具有明确标签、范围错误和坐标系提示。

## 测试与验收

### 后端单元和契约测试

- 地点规则覆盖来源与外部 ID 配对、名称/地址边界、经纬度边界、固定坐标系和规范化命令摘要。
- 行程命令覆盖首次关联、替换、移除、无引用移除、软删除项、跨行程 item、版本冲突、相同命令重放和相同 ID 不同载荷冲突。
- 相邻路段覆盖零/一个/多个地点、中间未解析地点、三种模式、49 段上限和折线确定性抽稀。
- 高德适配器使用本地 HTTP 替身覆盖成功、空结果、畸形 JSON、业务失败、超时、429、5xx、重试上限和敏感日志。
- 缓存测试覆盖新鲜命中、过期后实时刷新、供应商失败使用七天内陈旧缓存、超过七天不可用、Redis 读写失败不阻止实时结果。

### MySQL、Redis 与 HTTP 集成测试

- 迁移测试覆盖 dry-run、apply、重复执行、部分 DDL 恢复、结构差异和旧字段残留。
- 真实 MySQL 验证地点保存后重开、事务回滚、版本递增、负责人隔离和并发幂等收敛。
- 真实 Redis 与受控供应商替身验证路线缓存键、TTL、部分失败和无缓存降级。
- HTTP 测试逐条覆盖四个 #19 验收条件，并确认错误不包含 SQL、Key、其他成员 ID 或供应商原始响应。

### 前端测试

- `itineraryMapHttp` 是地点与地图 HTTP 的唯一适配器，测试 URL、请求体、认证和稳定错误解析。
- 地点抽屉测试搜索、选择确认、手工地点、失败保留输入、保存状态和版本冲突。
- 每日地图测试 Marker/列表顺序、路线模式切换、缓存标识、未解析地点、部分降级和 JS Loader 失败。
- 现有行程创建、编辑、规划建议和旧社区地图测试不得回归。

### 完整门禁

- Maven 单元测试、Testcontainers MySQL/Redis 集成测试。
- Vitest 全量测试和生产 Web 构建。
- Python 迁移、秘密与 CI 契约测试。
- `deployment` profile 生产包检查，确认不包含测试类、测试容器依赖、本机配置或真实 Key。
- Docker Compose 配置校验与 GitHub PR 全部 required checks。

浏览器验收使用带 `IT-TEST-#19` 标记的隔离数据：创建两天行程，为至少三个安排项确认搜索地点，添加一个手工地点，验证顺序 Marker、三种路线模式、刷新后保留、供应商故障缓存降级和无缓存文字降级。结束前精确删除测试行程并回查无残留。

## 验收标准映射

1. “搜索地点并把经过确认的地点引用加入安排项”由地点搜索、确认命令、正式地点表、地点抽屉及对应 HTTP/MySQL/Vue 测试证明。
2. “每日地图按照行程顺序展示地点，并提供相邻地点路线、距离和时间”由 `dayMapInput`、相邻路段编排、每日地图契约和顺序/路线测试证明。
3. “供应商响应通过适配器转换，不成为领域模型”由 Gateway 边界、统一 DTO、架构测试、无原始 JSON 持久化和数据文档证明。
4. “供应商失败时保留正式行程并允许缓存或手工地点”由事务隔离、Redis 新鲜/陈旧缓存、部分 200 响应、手工地点和故障集成测试证明。

## 明确不在范围内

- 真实逐向导航、实时定位共享、轨迹记录或计划与实际路线对比。
- 酒店、火车、航班搜索及第三方预订跳转。
- 地点收藏、全局地点主数据、地点去重合并或地理搜索引擎。
- 海外高德高级授权、多地图供应商自动切换、离线路网与自建路径算法。
- 路况推送、持续后台刷新、路线预计算队列或长期路线历史。
- 自动改写安排时间、自动选择交通方式或把路线时长写成正式计划。

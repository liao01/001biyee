# 每日地图数据链路

本文定义旅行行程地图的正式数据、接口和失效边界。地图能力只为旅行行程提供地点确认与按日路线参考；它不创建订单、导航记录或实际到访事实。

## 正式事实源与领域边界

- [20260905_itinerary_daily_map.sql](../../sql/migrations/20260905_itinerary_daily_map.sql) 是 `itinerary_item_place` 的正式升级脚本；[travel_share.sql](../../sql/travel_share.sql) 只为全新空库提供同一契约。
- `itinerary_item_place` 是已确认地点的唯一持久化事实源。Redis、HTTP 响应、前端状态、高德响应和路线折线均可丢弃或重建，不能反向写入正式行程。
- [旅行行程核心数据链路](itinerary.md) 是行程、日程日、条目、成员权限、命令幂等和版本检查的事实源。本页只说明地图扩展，不复制行程核心字段或状态规则。
- 领域语言以 [CONTEXT.md](../../CONTEXT.md) 中的“正式地点引用”和“路线参考”为准。

## 正式地点引用

`itinerary_item_place` 一行对应一个未删除行程条目的一个正式地点引用。主键为 `item_id`，并以 `itinerary_id` 建立索引；外键要求条目存在、行程存在，应用服务还必须验证条目和行程同属一个已锁定的聚合。

| 字段组 | 约束与含义 |
| --- | --- |
| 归属 | `item_id` 为主键，`itinerary_id` 必填；不能以两个独立外键代替同一聚合校验。 |
| 来源 | `source` 仅为 `AMAP` 或 `MANUAL`。`AMAP` 必须有 `source_place_id`，`MANUAL` 必须没有该值。 |
| 展示快照 | `name` 必填；`address` 与六位 `administrative_area_code` 可选。只保存供应商无关的确认快照，不保存供应商原始 JSON。 |
| 坐标 | `longitude`、`latitude` 均必填且分别位于经纬度合法范围内；`coordinate_system` 固定为 `GCJ02`。 |
| 审计 | `confirmed_at` 和 `updated_at` 必填。更新地点仍经过行程命令、成员权限和期望版本检查。 |

迁移默认仅报告差异。只有显式设置 `@apply_itinerary_daily_map_migration = 1` 才会在空表或缺失表上收敛结构；已有业务行出现结构漂移时迁移中止，不臆造或覆盖地点数据。

## HTTP 读写边界

所有接口都位于 `/web`，从统一身份会话取得当前会员，响应带 `Cache-Control: no-store`。Controller 只进行 HTTP 转换，权限、幂等、版本和聚合归属校验均委托正式应用服务。

| 接口 | 行为 |
| --- | --- |
| `GET /web/places/search` | 按关键词、可选城市和 1–20 的数量限制搜索候选地点；结果只是候选，不是正式地点引用。 |
| `PUT /web/itineraries/{itineraryId}/items/{itemId}/place` | 用命令 UUID、期望版本和已规范化的地点快照确认或替换一个条目的正式地点引用。 |
| `DELETE /web/itineraries/{itineraryId}/items/{itemId}/place` | 用同样的命令/版本边界移除地点引用。 |
| `GET /web/itineraries/{itineraryId}/days/{dayId}/map` | 返回该日的停靠点和相邻路线参考；`mode` 只可为 `WALKING`、`DRIVING` 或 `TRANSIT`。 |

地点搜索的错误可明确返回“服务暂不可用”；用户仍可确认经前端校验的 `MANUAL` GCJ02 地点。每日地图的未解析条目保持在停靠点列表中，不会被隐藏或伪造路线。

## Gateway 转换与失败降级

高德适配器只存在于 `itinerarymap.infrastructure.amap`：地点搜索把 POI 响应转换为供应商无关候选，路线接口只提取首个可用方案的距离、预计时长和折线。高德 DTO、密钥和原始响应不能进入领域模型、行程 Mapper、数据库或浏览器状态。

每条路线参考只连接相邻的已解析正式地点引用。公共交通要求两端可归并为同一城市行政区；不满足时该路线段为 `UNAVAILABLE`。调用有连接/响应时限、响应大小上限、至多一次重试和最多四条实时路线并发；单段失败不会中止其他路线段，也不会改变行程事实。

失败降级遵循以下顺序：新鲜缓存显示为 `CACHED`；供应商可用时显示为 `LIVE`；实时失败时七天窗口内的旧缓存仍可显示并标记 `stale`；再不可用时显示为 `UNAVAILABLE` 及机器可读原因。停靠点和文本路线列表始终保留，前端地图 SDK 加载失败时也可阅读这些文本信息。

## Redis 缓存

路线参考使用 `itinerary-map:route:v1:<sha256>` 作为 key。哈希输入包含供应商标识、方式、规范化起终点 GCJ02 坐标和公共交通城市上下文，避免把原始地点名、用户身份或供应商响应写进 key。

- Redis TTL 为 7 天；路线在计算后 24 小时内为新鲜缓存。
- 缓存只保存可再生路线参考，不保存正式地点引用或权限判断结果。
- Redis 读取、写入、序列化或删除畸形值失败时一律按缓存未命中处理；缓存故障不能把可得到的实时路线变成业务写入失败。

## 配置与秘密归属

后端唯一的高德 Web Service 凭据是 `AMAP_WEB_SERVICE_KEY`，映射到 `app.map.amap.api-key`；超时、并发和响应上限也由后端配置控制。它只供服务端 Gateway 请求高德，绝不进入 Vue 构建物、公开运行时配置或日志。

浏览器地图 JS key 是另一种公开凭据：生产前端容器只在启动时将 `AMAP_JS_KEY` 写入 `runtime-config.js`。`AMAP_JS_SECURITY_CODE` 只在 Nginx `/_AMapService/` 同源代理中追加给上游，不能写入公开脚本。开发环境可使用明确的本机 `VITE_AMAP_JS_KEY` 与 `VITE_AMAP_JS_SECURITY_CODE`；仓库示例值仅为 `change-me`，真实值必须由被 Git 忽略的本机文件或部署平台 Secrets 注入。

## 测试数据与旧社区地图

`ItineraryMapHttpIT` 为本批次创建独立 MySQL、Redis 和高德替身容器，使用专用标签和批次名称；每个测试后删除路线 key 与行程表数据，类结束后停止容器并回查。`RedisRouteReferenceCacheIT` 同样只操作测试容器。统一运行器在容器模式清除宿主数据库和 Redis 环境变量，不会连接用户正在使用的服务。

旧的社区帖子地图只展示帖子位置，继续属于社区浏览边界；它不读取或写入 `itinerary_item_place`，也不承担行程地点确认、路线计算或执行记录。新每日地图只从正式行程接口读取，前端不得根据社区地图状态推断行程事实。

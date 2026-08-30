# 腾讯云轻量服务器资源受限共存部署设计

## 背景

旅游分享平台需要部署到腾讯云轻量应用服务器 `lhins-7kz2kukv`（公网 IP `42.193.184.77`）。该实例位于广州，当前规格为 2 核 CPU、2GB 内存、40GB 系统盘，运行 Ubuntu Server 24.04 LTS。

服务器已经运行 Dify 1.17.0 的完整 Docker Compose 栈，Dify 必须保留在同一实例并继续占用 80/443 端口。诊断期间发现 Dify 的 `worker` 和 `worker_beat` 因 `/opt/dify/.env` 使用无效的 `LOG_LEVEL=WARN` 而持续退出并被 Docker 拉起。该配置已备份到 `/opt/dify/.env.codex-backup-20260830-1026`，修正为 `LOG_LEVEL=INFO` 后，两个 Celery 进程以 INFO 级别稳定运行、重启计数保持为 0，CPU 从各约 90% 回落至接近 0%。

修复后 Dify 仍使用约 1.4GB 内存，实例可用内存约 500MB，并已使用 Swap。用户明确要求不升级实例，在同一台机器上保留 Dify 并部署旅游平台。因此本设计采用资源受限、隔离优先、可快速停止与回滚的共存方案，不承诺等同于扩容后的生产容量。

## 目标

- 保留 Dify 根地址、容器、数据库和数据卷。
- 在独立 Docker Compose 项目中部署旅游平台用户端、管理端、后端、MySQL、MongoDB 和 Redis。
- 使用现有 Dify Nginx 暴露 `/travel/`、`/travel-admin/` 和 `/business/`，不新增公网端口。
- 使用全新的 `travel_share` 数据库，不迁移本机现有业务数据。
- 将上传目录、API 基础地址和前端路由基础路径收敛为可配置事实源。
- 真实凭据只存在于服务器 root 可读环境文件，不进入 Git、Jar、镜像、Compose 文件或日志。
- 为全部新增容器设置资源上限、健康检查和可恢复的持久化目录。
- 验证旅游平台上线时不破坏 Dify；若资源不足则停止旅游平台并保留数据。

## 非目标

- 不升级腾讯云实例套餐。
- 不迁移本机 MySQL 中的用户、帖子或其他业务数据。
- 不停止、替换或降级 Dify 服务。
- 不复用 Dify 的 PostgreSQL、Redis 或其他数据组件作为旅游平台正式数据源。
- 不开放 MySQL、MongoDB、Redis 或 Spring Boot 的公网端口。
- 不在本次部署中购买域名、证书、托管数据库或其他云服务。
- 不把旅游平台改写为 PostgreSQL，也不重构现有业务领域模型。
- 不在生产环境创建临时用户、帖子或其他需要清理的验证数据。

## 部署拓扑

旅游平台使用独立 Compose 项目，根目录为 `/opt/lyw`。主要目录如下：

```text
/opt/lyw/
├── compose.yaml
├── current -> releases/<release-id>
├── releases/<release-id>/
│   ├── backend/
│   └── frontend/
├── data/
│   ├── mysql/
│   ├── mongo/
│   ├── redis/
│   └── uploads/
├── secrets/runtime.env
├── backups/
└── nginx/
```

Compose 项目包含五个容器：

| 容器 | 职责 | 网络 |
| --- | --- | --- |
| `lyw-mysql` | `travel_share` 唯一正式业务数据库 | `lyw_internal` |
| `lyw-mongo` | AI 聊天记忆文档 | `lyw_internal` |
| `lyw-redis` | 验证码、登录状态和统计缓存 | `lyw_internal` |
| `lyw-backend` | Spring Boot 业务 API 与上传文件读取 | `lyw_internal`、Dify 外部网络 |
| `lyw-frontend` | 同时托管用户端与管理端静态产物 | Dify 外部网络 |

`lyw_internal` 是旅游平台私有网络。数据库容器不加入 Dify 网络，不发布宿主机端口。`lyw-backend` 和 `lyw-frontend` 加入 Dify Compose 已有的外部网络 `dify_default`，使 `dify-nginx-1` 能通过容器 DNS 名称访问它们。部署前必须再次以 `docker network ls` 验证该网络存在，不在网络缺失时创建同名替代网络。

## 公网入口与路径

Dify 继续占用根地址及其现有 API 路径。新增入口为：

| 公网路径 | 上游 |
| --- | --- |
| `/travel/` | `lyw-frontend` 用户端静态站点 |
| `/travel-admin/` | `lyw-frontend` 管理端静态站点 |
| `/business/` | `lyw-backend`，保留内部 `/lyw/` 上下文 |

前端正式 API 基础地址为同源相对路径 `/business`。现有业务请求继续使用 `/lyw/...`，最终请求形态为 `/business/lyw/...`。Dify Nginx 移除外层 `/business` 前缀后把 `/lyw/...` 原样传给后端，避免在本次部署中批量改变后端接口契约。

用户端 Vite 基础路径为 `/travel/`，管理端为 `/travel-admin/`。两个 Vue Router 均使用构建工具提供的基础路径创建 history，前端静态 Nginx 分别为两个入口提供对应的 `try_files` 回退，保证深链接刷新不会返回 404。

前端只保留一个 API 地址事实源。`VITE_BASE_URL` 是正式构建变量，`main.js`、Axios 实例和各页面统一消费该变量；不再并行维护 `VITE_SERVER`，也不保留 `localhost:8080` 硬编码调用。

## 上传文件事实源

后端当前在 `PostService`、`MapService`、`UserProFileService` 和 `WebMvcConfig` 中分别维护 Windows 绝对路径。本次新增单一的存储配置对象，以 `app.storage.upload-dir` 为正式事实源。

- 本机默认值可继续指向本地开发目录。
- 生产值通过环境变量映射为 `/data/uploads`。
- 帖子图片写入根目录，头像写入 `avatar/`，位置图片写入 `location/`。
- 静态资源映射从同一配置对象生成文件 URI。
- 数据库仍只保存 `/uploads/...` 相对地址，不写服务器绝对路径。
- `/opt/lyw/data/uploads` 以持久化绑定挂载到后端 `/data/uploads`。

目录创建失败、非法文件名或写入失败时，后端返回现有业务错误并保持数据库事务回滚，不在其他目录静默降级。

## 数据初始化与持久化

### MySQL

`sql/travel_share.sql` 以只读方式挂载到 MySQL 的首次初始化目录，仅在空数据目录第一次启动时执行。数据库名固定为 `travel_share`。脚本中的 `DROP TABLE IF EXISTS` 只作用于全新的独立数据库，不接触 Dify PostgreSQL 或其他数据。

初始化完成后验证：

- 正式表数量与仓库快照一致。
- `post_category` 包含仓库定义的正式分类。
- 不存在迁移脚本应消除的旧字段或缺失列。
- MySQL 健康检查可使用应用账号访问 `travel_share`。

后续部署不重复执行初始化脚本。任何未来结构迁移必须先备份并使用独立、可重复执行的迁移流程。

### MongoDB、Redis 与上传文件

MongoDB、Redis 和上传目录使用 `/opt/lyw/data` 下的独立持久化目录。停止或重建应用容器不得删除这些目录。常规回滚使用 `docker compose down`，不使用 `down -v`，也不删除数据目录。

Redis 只服务旅游平台，不复用或读取 Dify Redis 的密码和数据。MongoDB 只保存旅游平台聊天记忆，不与 Dify 向量库或 PostgreSQL 混用。

## 运行时配置与秘密

`/opt/lyw/secrets/runtime.env` 权限设置为 root 可读，包含：

- MySQL、MongoDB、Redis 连接信息和随机生成密码。
- DeepSeek、通义千问、高德地图、邮件、JWT 和 Dify/RAG 运行时凭据。
- Spring Boot 生产 profile、上传目录和 JVM 参数。

数据库密码在服务器上生成。本机已有的外部服务凭据在用户明确授权后通过加密 SSH 通道传输。传输和部署流程不得把真实值打印到终端、日志或工具输出。

生产 Jar 使用显式 Maven `deployment` profile 构建。该 profile 从资源复制阶段排除本机被 Git 忽略的 `application.properties` 和 `application.yml`，并包含只引用环境变量的受版本控制生产 profile。日常本地构建行为保持不变。仓库生产配置只能提供无秘密默认值或必填失败行为。构建完成后检查 Jar 条目，并在不输出真实值的前提下用本机秘密值逐项扫描归档，命中数必须为 0。

## 资源控制

在不升级 2GB 实例的约束下，旅游平台采用以下初始上限：

- Spring Boot：`-Xms128m -Xmx256m`，最大元空间 128MB，容器上限 512MB。
- MySQL：容器上限 384MB，InnoDB 缓冲池 96MB，最大连接数 30，关闭 Performance Schema。
- MongoDB：容器上限 384MB，WiredTiger 缓存 0.25GB。
- Redis：容器上限 96MB，`maxmemory` 64MB，淘汰策略 `allkeys-lru`。
- 前端 Nginx：单容器托管两个静态站点，容器上限 64MB。

这些值是可验证的首发参数，不是保证占用量。任何调整必须一次只改变一个资源参数，并以容器重启、OOM、响应时间、Dify 状态和 Swap 变化验证；不得同时放宽多个容器上限。

所有容器使用 `restart: unless-stopped`，并配置实际健康检查。后端只在 MySQL、MongoDB 和 Redis 健康后启动。资源上限不得通过停止 Dify、复用 Dify 数据组件或取消健康检查来规避。

服务器预期会使用 Swap。部署后持续检查容器 OOM 标记、重启次数、CPU、内存、Swap、磁盘和 Dify HTTP 状态。若出现 OOM、持续重启、明显响应恶化或 Dify 健康回退，立即移除旅游平台公网入口并停止旅游平台容器，保留数据与发布产物等待后续处理。

## Dify Nginx 接入

Dify Nginx 的正式模板位于 `/opt/dify/nginx/conf.d/default.conf.template`，容器启动时生成 `/opt/dify/nginx/conf.d/default.conf`。接入流程必须同时维护模板和当前生成配置：

1. 备份两个文件到 `/opt/lyw/backups`，记录时间和校验和。
2. 使用唯一、可检索的 `BEGIN/END LYW ROUTES` 标记插入三组 location，不手工复制整份 server 配置。
3. location 使用 Docker 内部 DNS 和变量形式的 upstream，保持与 Dify 现有配置风格一致。
4. 修改后在容器内执行 `nginx -t`。
5. 只有语法检查通过并且上游容器健康时才 reload Nginx。
6. reload 后同时验证 Dify 根页面及三个旅游平台入口。

任一步失败时恢复备份、重新执行 `nginx -t` 并 reload。不得在语法失败时重启或替换 Dify Nginx 容器。

## 本地构建与发布

服务器不安装 Maven、Node.js 或 npm，也不在服务器执行前端打包。发布流程为：

1. 在本机运行后端测试、前端 Vitest 和三个生产构建。
2. 生成不含秘密的后端 Jar 和两个静态目录。
3. 为产物生成 SHA-256 校验和与发布标识。
4. 通过 SSH 上传到 `/opt/lyw/releases/<release-id>` 临时目录。
5. 在服务器核对校验和后原子移动为正式发布目录。
6. Compose 引用版本化发布目录；验证成功后更新 `current` 符号链接。

服务器配置、Compose 和 Nginx 规则与应用产物分离。上传中断或校验失败的临时目录在任务结束前精确删除；不能删除时按项目规则记录待清理事项。

## 测试策略

### 后端

- 存储配置能从环境变量解析 Linux 目录。
- 帖子、头像和位置图片都从同一存储事实源解析子目录。
- 静态资源映射使用同一目录 URI。
- 写入失败时不产生数据库半成品记录。
- 生产 profile 不包含真实秘密，缺少必填秘密时明确启动失败。
- 现有后端测试全量通过。

### 前端

- 用户端 Router 使用 `/travel/` 基础路径。
- 管理端 Router 使用 `/travel-admin/` 基础路径。
- 深链接在对应静态站点内回退到正确的 `index.html`。
- API 请求统一组合为 `/business/lyw/...`。
- 清除两处 `localhost:8080` 和并行的 `VITE_SERVER` 入口后，相关登录、验证码、AI 和管理操作仍使用正式地址事实源。
- 用户端现有 53 项测试和生产构建通过；管理端生产构建通过。

### 部署验证

- `docker compose ps` 显示全部旅游平台容器健康，重启计数为 0，OOM 标记为 false。
- MySQL、MongoDB 和 Redis 不监听公网地址。
- `GET /business/lyw/web/post/categories` 返回正式分类。
- `/travel/`、用户端深链接、`/travel-admin/` 和管理端深链接返回正确站点。
- `/business/lyw/uploads/...` 能读取持久化目录中的资源。
- Dify 根页面和现有 API 路径在 Nginx reload 前后保持可用。
- 观察期内 CPU、内存和 Swap 没有持续恶化，Dify 与旅游平台容器均无重启。

验证只使用读接口和静态资源，不创建临时用户、帖子、评论或位置数据。

## 错误处理与回滚

- 数据库初始化失败：停止旅游平台容器，保留日志和数据目录，不接入 Nginx；确认原因后从空目录重新初始化仍需明确核对目标目录。
- 后端健康失败：读取已脱敏日志，定位 MySQL、MongoDB、Redis、配置或外部 API 边界，不通过扩大内存盲目重试。
- 前端路径失败：在本地修复构建基础路径并生成新 release，不直接修改构建产物。
- Nginx 语法或路由失败：恢复备份并 reload，Dify 优先恢复。
- 资源不足：移除带标记的 LYW location，执行 `docker compose stop`，保留数据和发布目录。
- 应用版本回滚：把 Compose 或 `current` 指向上一 release，重建应用容器；不回滚数据库结构，除非对应迁移有单独恢复方案。

部署回滚不删除 `/opt/lyw/data`，不运行递归强制删除，不执行 `docker compose down -v`，不修改或删除 Dify 数据卷。

## 验收标准

- Dify 根页面、API、数据库和数据卷保持可用。
- 用户端可从 `/travel/` 打开，管理端可从 `/travel-admin/` 打开，深链接刷新正常。
- 后端通过 `/business/lyw/...` 提供服务，数据库和缓存不暴露公网。
- 新 `travel_share` 数据库成功初始化并包含正式分类数据。
- 帖子、头像和位置图片统一写入 `/opt/lyw/data/uploads` 并能通过后端读取。
- 真实秘密不出现在 Git、Jar、镜像、Compose、Nginx 配置或日志中。
- 本地自动化测试和生产构建全部通过。
- 旅游平台容器健康、无 OOM、无持续重启；Dify 容器状态不回退。
- Nginx 修改有备份、标记、语法验证和明确回滚路径。
- 若 2GB 资源无法支撑共存，旅游平台被安全停止且 Dify 保持可用，不能把不稳定状态报告为部署完成。

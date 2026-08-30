# 腾讯云轻量服务器资源受限共存部署 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留同机 Dify 的前提下，把旅游分享平台以独立、资源受限、可回滚的 Docker Compose 栈部署到 `/travel/`、`/travel-admin/` 和 `/business/`。

**Architecture:** 本地以 TDD 完成前端子路径、统一 API 地址、后端存储目录和无秘密生产构建；服务器只接收版本化 Jar、静态文件和部署配置。旅游平台使用独立 MySQL、MongoDB、Redis 和私有网络，仅前后端加入现有 `dify_default` 网络，由 Dify Nginx 转发三个前缀。

**Tech Stack:** Java 17、Spring Boot 3.4、Maven、Vue 3、Vite、Vitest、Python unittest/pytest、Docker Compose、MySQL 8、MongoDB 7、Redis 7、Nginx、PowerShell、Bash。

**Spec:** `docs/superpowers/specs/2026-08-30-tencent-lighthouse-constrained-deployment-design.md`

## Global Constraints

- Dify 必须保留在实例 `lhins-7kz2kukv`（`42.193.184.77`）并继续占用 80/443；不得停止、替换或复用其数据库与 Redis。
- 腾讯云实例保持 2 核、2GB 内存、40GB 系统盘，不执行套餐升级或购买新服务。
- 公网入口固定为 `/travel/`、`/travel-admin/`、`/business/`；数据库和后端不发布宿主机公网端口。
- 共享 Docker 网络的正式名称是 `dify_default`；部署前必须验证存在，不创建同名替代网络。
- MySQL 使用全新 `travel_share` 数据库；不迁移本机业务数据，不创建线上临时用户或帖子。
- 上传目录正式配置是 `app.storage.upload-dir`，生产值为 `/data/uploads`。
- 真实秘密只允许存在于本机 Git 忽略配置和服务器 `/opt/lyw/secrets/runtime.env`；不得进入 Git、Jar、镜像、Compose、Nginx 或日志。
- 初始资源上限：后端 512MB、MySQL 384MB、MongoDB 384MB、Redis 96MB、前端 Nginx 64MB；JVM 使用 `-Xms128m -Xmx256m -XX:MaxMetaspaceSize=128m`。
- Nginx 修改前备份模板和生成配置，修改后先 `nginx -t` 再 reload；失败立即恢复备份。
- 线上部署不使用 `docker compose down -v`，不删除 `/opt/lyw/data`，不递归删除工作区、用户目录或 `/opt/lyw`。
- 当前 Git 为 detached HEAD 且含用户未提交改动。计划中的“建议提交”仅记录提交边界；未取得用户明确提交授权、未处于安全分支时不得执行 `git commit`。
- 不创建 worktree；若执行阶段认为必须隔离，先说明原因并取得用户明确授权。

---

### Task 1: 建立后端上传目录单一事实源

**Files:**
- Create: `business/src/main/java/com/jiawa/lyw/config/StorageProperties.java`
- Create: `business/src/test/java/com/jiawa/lyw/config/StoragePropertiesTests.java`
- Modify: `business/src/main/java/com/jiawa/lyw/config/WebMvcConfig.java`
- Modify: `business/src/main/java/com/jiawa/lyw/service/PostService.java`
- Modify: `business/src/main/java/com/jiawa/lyw/service/MapService.java`
- Modify: `business/src/main/java/com/jiawa/lyw/service/UserProFileService.java`

**Interfaces:**
- Produces: `StorageProperties#getUploadDir(): Path`
- Produces: `StorageProperties#postsDir(): Path`
- Produces: `StorageProperties#avatarsDir(): Path`
- Produces: `StorageProperties#locationsDir(): Path`
- Produces: `StorageProperties#resourceLocation(): URI`
- Consumes: Spring property `app.storage.upload-dir`

- [ ] **Step 1: 写路径行为的失败测试**

```java
class StoragePropertiesTests {
    @TempDir Path tempDir;

    @Test
    void derivesAllUploadLocationsFromOneRoot() {
        StorageProperties properties = new StorageProperties();
        properties.setUploadDir(tempDir.resolve("uploads"));

        assertThat(properties.postsDir()).isEqualTo(tempDir.resolve("uploads"));
        assertThat(properties.avatarsDir()).isEqualTo(tempDir.resolve("uploads/avatar"));
        assertThat(properties.locationsDir()).isEqualTo(tempDir.resolve("uploads/location"));
        assertThat(properties.resourceLocation()).isEqualTo(tempDir.resolve("uploads").toUri());
    }
}
```

- [ ] **Step 2: 运行测试并确认因类型不存在而失败**

Run: `business\mvnw.cmd -pl business -Dtest=StoragePropertiesTests test`

Expected: FAIL，编译器报告 `StorageProperties` 不存在。

- [ ] **Step 3: 实现最小配置对象**

```java
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {
    private Path uploadDir = Path.of("D:/idea/lyw/uploads");

    public Path getUploadDir() { return uploadDir.toAbsolutePath().normalize(); }
    public void setUploadDir(Path uploadDir) { this.uploadDir = uploadDir; }
    public Path postsDir() { return getUploadDir(); }
    public Path avatarsDir() { return getUploadDir().resolve("avatar"); }
    public Path locationsDir() { return getUploadDir().resolve("location"); }
    public URI resourceLocation() { return getUploadDir().toUri(); }
}
```

- [ ] **Step 4: 运行目标测试并确认通过**

Run: `business\mvnw.cmd -pl business -Dtest=StoragePropertiesTests test`

Expected: PASS，1 test，0 failures。

- [ ] **Step 5: 把四个调用方改为消费配置对象**

具体替换：

- `WebMvcConfig` 注入 `StorageProperties`，`addResourceLocations(storageProperties.resourceLocation().toString())`。
- `PostService` 删除 `D:/idea/lyw/uploads/`，保存前执行 `Files.createDirectories(storageProperties.postsDir())`，目标使用 `postsDir().resolve(newFileName)`。
- `MapService` 删除静态 `UPLOAD_DIR`，保存前创建 `locationsDir()`，目标使用 `locationsDir().resolve(fileName)`。
- `UserProFileService` 删除静态 `UPLOAD_DIR`，保存前创建 `avatarsDir()`，目标使用 `avatarsDir().resolve(newFileName)`。

所有返回给数据库或前端的 URL 仍为 `/uploads/...`，不得写入 `/data/uploads`。

- [ ] **Step 6: 增加目录调用方防回流断言**

在 `StoragePropertiesTests` 增加源码扫描断言，读取上述四个 Java 文件，验证不存在 `D:/idea/lyw/uploads` 或注释中的 `/home/lyw/uploads`，并验证均引用 `StorageProperties`。

- [ ] **Step 7: 运行后端全量测试**

Run: `business\mvnw.cmd -pl business test`

Expected: 现有 38 项加新增测试全部 PASS，0 failures，0 errors。

- [ ] **Step 8: 检查意图内 diff 并记录建议提交**

Run: `git diff -- business/src/main/java/com/jiawa/lyw/config business/src/main/java/com/jiawa/lyw/service business/src/test/java/com/jiawa/lyw/config`

Suggested commit: `feat: make upload storage configurable`

Do not commit without explicit authorization and a safe branch.

---

### Task 2: 收敛前端基础路径与 API 地址

**Files:**
- Create: `web/src/utils/baseUrl.test.js`
- Create: `tests/scripts/test_deployment_frontend_contract.py`
- Modify: `web/src/utils/baseUrl.js`
- Modify: `web/src/main.js`
- Modify: `web/src/router/index.js`
- Modify: `web/vite.config.js`
- Modify: `web/.env.production`
- Modify: `web/src/view/page/ai.vue`
- Modify: `admin/src/utils/baseUrl.js`
- Modify: `admin/src/main.js`
- Modify: `admin/src/router/index.js`
- Modify: `admin/vite.config.js`
- Modify: `admin/.env.production`
- Modify: `admin/src/components/deleteUser.vue`

**Interfaces:**
- Produces: `normalizeBaseUrl(value: string | undefined): string`
- Produces: `buildApiUrl(path: string, base?: string): string`
- Produces: user Vite base `/travel/`
- Produces: admin Vite base `/travel-admin/`
- Consumes: `VITE_BASE_URL=/business`

- [ ] **Step 1: 写 API 地址纯函数失败测试**

```javascript
import { describe, expect, it } from 'vitest'
import { normalizeBaseUrl, buildApiUrl } from './baseUrl.js'

describe('deployment API base', () => {
  it('normalizes the configured prefix and joins legacy /lyw routes', () => {
    expect(normalizeBaseUrl('/business/')).toBe('/business')
    expect(buildApiUrl('/lyw/web/post/categories', '/business/'))
      .toBe('/business/lyw/web/post/categories')
  })
})
```

- [ ] **Step 2: 运行测试并确认导出缺失导致失败**

Run: `npm test -- src/utils/baseUrl.test.js`

Workdir: `web`

Expected: FAIL，提示 `normalizeBaseUrl` 或 `buildApiUrl` 未导出。

- [ ] **Step 3: 实现唯一 API 地址工具**

```javascript
export const normalizeBaseUrl = (value = '') => {
  const normalized = value.trim().replace(/\/+$/, '')
  return normalized === '/' ? '' : normalized
}

export const buildApiUrl = (path, base = import.meta.env.VITE_BASE_URL) => {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${normalizeBaseUrl(base)}${normalizedPath}`
}

export const BASE_URL = normalizeBaseUrl(import.meta.env.VITE_BASE_URL)
```

管理端 `baseUrl.js` 使用同样签名，避免第三套规则。

- [ ] **Step 4: 运行目标测试并确认通过**

Run: `npm test -- src/utils/baseUrl.test.js`

Workdir: `web`

Expected: PASS。

- [ ] **Step 5: 写部署路径契约失败测试**

`tests/scripts/test_deployment_frontend_contract.py` 使用 `unittest` 读取文件并断言：

```python
def test_frontend_deployment_contract(self):
    self.assertIn("base: mode === 'production' ? '/travel/' : '/'", web_vite)
    self.assertIn("createWebHistory(import.meta.env.BASE_URL)", web_router)
    self.assertIn("base: mode === 'production' ? '/travel-admin/' : '/'", admin_vite)
    self.assertIn("createWebHistory(import.meta.env.BASE_URL)", admin_router)
    self.assertEqual("VITE_BASE_URL=/business", web_env.strip())
    self.assertEqual("VITE_BASE_URL=/business", admin_env.strip())
    self.assertNotIn("VITE_SERVER", combined_sources)
    self.assertNotIn("http://localhost:8080", combined_sources)
```

- [ ] **Step 6: 运行契约测试并确认当前失败原因正确**

Run: `python -m unittest tests.scripts.test_deployment_frontend_contract -v`

Expected: FAIL，至少报告 Vite base、Router base、`VITE_SERVER` 和 localhost 硬编码不符合契约。

- [ ] **Step 7: 实现子路径与 API 收敛**

- `web/vite.config.js` 改为 `defineConfig(({ mode }) => ({ base: mode === 'production' ? '/travel/' : '/', ... }))`。
- `admin/vite.config.js` 同理使用 `/travel-admin/`。
- 两个 Router 改为 `createWebHistory(import.meta.env.BASE_URL)`。
- 两个 `.env.production` 仅保留 `VITE_BASE_URL=/business`。
- 两个 `main.js` 导入 `BASE_URL` 并设置 `axios.defaults.baseURL = BASE_URL`，删除 `VITE_SERVER`。
- `ai.vue` 和 `deleteUser.vue` 用 `buildApiUrl('/lyw/...')` 替换 localhost 地址。

- [ ] **Step 8: 运行前端契约与用户端全量测试**

Run: `python -m unittest tests.scripts.test_deployment_frontend_contract -v`

Run: `npm test`

Workdir for second command: `web`

Expected: 契约 PASS；用户端现有 53 项加新增测试全部 PASS。

- [ ] **Step 9: 运行两个生产构建并检查资源前缀**

Run: `npm run build` in `web`

Run: `npm run build` in `admin`

Run: `rg -n '(src|href)="/(?!travel/)' web/dist/index.html` and equivalent `/travel-admin/` check for admin using a regex-capable shell; expected no wrong absolute asset prefix.

Expected: 两个构建退出码 0；用户端资源以 `/travel/` 开头，管理端以 `/travel-admin/` 开头。

- [ ] **Step 10: 检查 diff 并记录建议提交**

Suggested commit: `feat: support prefixed production routes`

Do not commit without explicit authorization and a safe branch.

---

### Task 3: 建立无秘密生产后端构建

**Files:**
- Create: `business/src/main/resources/application-prod.properties`
- Create: `tests/scripts/test_deployment_jar_contract.py`
- Modify: `business/pom.xml`
- Modify: `business/src/main/resources/application.properties.example`

**Interfaces:**
- Produces: Maven profile `deployment`
- Produces: Spring profile `prod`
- Consumes: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_*`, `MONGODB_URI`, `DEEPSEEK_*`, `DASHSCOPE_*`, `MAIL_*`, `JWT_SECRET`, `RAG_*`, `AMAP_API_KEY`, `UPLOAD_DIR`

- [ ] **Step 1: 写生产配置与 Jar 契约失败测试**

`test_deployment_jar_contract.py` 必须：

1. 解析 `business/pom.xml`，确认存在 `deployment` profile。
2. 确认该 profile 排除 `application.properties` 和 `application.yml`。
3. 确认 `application-prod.properties` 只出现 `${ENV_NAME...}` 引用，不出现本机真实值。
4. 给定 Jar 路径时用 `zipfile.ZipFile.namelist()` 断言不存在：

```python
forbidden = {
    "BOOT-INF/classes/application.properties",
    "BOOT-INF/classes/application.yml",
}
self.assertTrue(forbidden.isdisjoint(jar_entries))
self.assertIn("BOOT-INF/classes/application-prod.properties", jar_entries)
```

- [ ] **Step 2: 运行静态契约并确认失败**

Run: `python -m unittest tests.scripts.test_deployment_jar_contract -v`

Expected: FAIL，报告 profile 或生产配置缺失。

- [ ] **Step 3: 添加 deployment profile 与生产配置**

`application-prod.properties` 以仓库 example 为基线，增加：

```properties
server.servlet.context-path=/lyw
server.port=8080
server.address=0.0.0.0
app.storage.upload-dir=${UPLOAD_DIR:/data/uploads}
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.data.redis.host=${REDIS_HOST:lyw-redis}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.password=${REDIS_PASSWORD}
spring.data.mongodb.uri=${MONGODB_URI}
```

其余 AI、邮件、JWT、RAG 和地图项逐项使用现有 example 中的环境变量名；真实密钥不写入文件。`deployment` profile 的 resources 明确排除未带 profile 的本机配置并保留 mapper、日志配置、prompt 和 `application-prod.properties`。

- [ ] **Step 4: 运行静态契约并确认通过**

Run: `python -m unittest tests.scripts.test_deployment_jar_contract -v`

Expected: 静态部分 PASS。

- [ ] **Step 5: 运行 deployment 构建**

Run: `business\mvnw.cmd -pl business -Pdeployment clean package`

Expected: BUILD SUCCESS，后端全量测试通过，生成 `business/target/business-0.0.1-SNAPSHOT.jar`。

- [ ] **Step 6: 对实际 Jar 执行归档契约与秘密扫描**

Run: `python -m unittest tests.scripts.test_deployment_jar_contract -v`

秘密扫描脚本从本机忽略配置中读取非空敏感值，在内存中逐项扫描解压后的 Jar；输出只能是键名与命中数，不能输出值。Expected: 所有敏感键命中数 0。

- [ ] **Step 7: 检查 diff 并记录建议提交**

Suggested commit: `build: add secret-free deployment profile`

Do not commit without explicit authorization and a safe branch.

---

### Task 4: 创建资源受限 Compose 与运行镜像

**Files:**
- Create: `deploy/compose.yaml`
- Create: `deploy/backend/Dockerfile`
- Create: `deploy/frontend/Dockerfile`
- Create: `deploy/frontend/default.conf`
- Create: `deploy/mysql/low-memory.cnf`
- Create: `deploy/nginx/lyw-locations.conf.inc`
- Create: `tests/scripts/test_deployment_compose_contract.py`

**Interfaces:**
- Produces: Compose services `lyw-mysql`, `lyw-mongo`, `lyw-redis`, `lyw-backend`, `lyw-frontend`
- Produces: private network `lyw_internal`
- Consumes: external network `dify_default`
- Consumes: `/opt/lyw/secrets/runtime.env`

- [ ] **Step 1: 写 Compose 静态失败测试**

测试读取 `deploy/compose.yaml` 并断言：

- 五个正式容器名存在。
- 数据库服务没有 `ports:`。
- 只有 backend/frontend 引用 `dify_default`。
- `dify_default.external` 为 true。
- 每个服务存在 `mem_limit`、`restart: unless-stopped` 和健康检查；纯静态前端可使用 HTTP healthcheck。
- 数据目录均位于 `/opt/lyw/data`。
- Compose 文本不包含任何本机真实密码或 API key。

- [ ] **Step 2: 运行测试并确认部署文件缺失导致失败**

Run: `python -m unittest tests.scripts.test_deployment_compose_contract -v`

Expected: FAIL，报告 `deploy/compose.yaml` 不存在。

- [ ] **Step 3: 创建运行镜像**

Backend Dockerfile 使用固定 Java 17 JRE 基础镜像，仅复制 `app.jar`，创建非 root 用户，入口为：

```dockerfile
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
```

JVM 内存参数通过 `JAVA_TOOL_OPTIONS` 注入。Frontend Dockerfile 使用固定 Nginx Alpine 基础镜像，复制：

```text
web/dist     -> /usr/share/nginx/html/travel
admin/dist   -> /usr/share/nginx/html/travel-admin
```

Frontend `default.conf` 为 `/travel/` 和 `/travel-admin/` 分别提供 `try_files ... /<prefix>/index.html`，不监听宿主机端口。

- [ ] **Step 4: 创建低内存数据库配置与 Compose**

`low-memory.cnf` 至少包含：

```ini
[mysqld]
innodb-buffer-pool-size=96M
max-connections=30
performance-schema=OFF
skip-name-resolve=ON
```

Compose 的关键资源参数必须与规格一致：512m、384m、384m、96m、64m。Mongo 命令包含 `--wiredTigerCacheSizeGB 0.25`；Redis 使用 `--maxmemory 64mb --maxmemory-policy allkeys-lru`。数据库健康检查引用容器环境变量名，不把真实值展开进 Compose 文本。

- [ ] **Step 5: 创建带标记的 Dify Nginx location 片段**

片段必须含唯一标记：

```nginx
# BEGIN LYW ROUTES
location = /travel { return 301 /travel/; }
location /travel/ {
  set $lyw_frontend_upstream lyw-frontend:80;
  proxy_pass http://$lyw_frontend_upstream;
  include proxy.conf;
}
location = /travel-admin { return 301 /travel-admin/; }
location /travel-admin/ {
  set $lyw_frontend_upstream lyw-frontend:80;
  proxy_pass http://$lyw_frontend_upstream;
  include proxy.conf;
}
location /business/ {
  set $lyw_backend_upstream lyw-backend:8080;
  rewrite ^/business(/.*)$ $1 break;
  proxy_pass http://$lyw_backend_upstream;
  include proxy.conf;
  client_max_body_size 100m;
  proxy_read_timeout 3600s;
}
# END LYW ROUTES
```

- [ ] **Step 6: 运行 Compose 契约测试**

Run: `python -m unittest tests.scripts.test_deployment_compose_contract -v`

Expected: PASS。

- [ ] **Step 7: 检查供应链与镜像引用**

所有基础镜像必须使用明确版本标签；部署前在服务器拉取后记录镜像 digest。运行仓库安全扫描：

Run: `python scripts/security/scan_repository.py`

Expected: 无秘密泄露或高危阻塞。

- [ ] **Step 8: 检查 diff 并记录建议提交**

Suggested commit: `ops: add constrained docker deployment`

Do not commit without explicit authorization and a safe branch.

---

### Task 5: 创建可重复发布、Nginx 更新与验证脚本

**Files:**
- Create: `scripts/deploy/build_release.ps1`
- Create: `scripts/deploy/prepare_server.sh`
- Create: `scripts/deploy/update_dify_nginx.sh`
- Create: `scripts/deploy/verify_server.sh`
- Create: `tests/scripts/test_deployment_scripts_contract.py`

**Interfaces:**
- Produces: local release archive and `SHA256SUMS`
- Produces: `/opt/lyw/{releases,data,secrets,backups,nginx}`
- Produces: idempotent LYW Nginx marker insertion/removal
- Consumes: approved spec paths and container names

- [ ] **Step 1: 写脚本安全契约失败测试**

测试必须拒绝以下模式：

```python
for forbidden in ("rm -rf", "docker compose down -v", "docker system prune", "git reset --hard"):
    self.assertNotIn(forbidden, combined_scripts)
self.assertIn("BEGIN LYW ROUTES", nginx_script)
self.assertIn("nginx -t", nginx_script)
self.assertIn("/opt/lyw/backups", nginx_script)
self.assertIn("SHA256SUMS", build_script)
```

- [ ] **Step 2: 运行测试并确认脚本缺失导致失败**

Run: `python -m unittest tests.scripts.test_deployment_scripts_contract -v`

Expected: FAIL。

- [ ] **Step 3: 实现本地 release 构建脚本**

`build_release.ps1` 必须：

- 以 `Set-StrictMode -Version Latest` 和 `$ErrorActionPreference = 'Stop'` 开始。
- 接收显式输出目录，不使用 `$HOME` 或工作区根作为删除目标。
- 调用 Task 1–4 的完整测试和构建命令。
- 复制 Jar、两个 dist、deploy 文件到新 release 目录。
- 生成 SHA-256 清单。
- 调用 Jar 秘密扫描并在任何命中时失败。
- 只清理自己创建且已验证位于显式输出根下的临时目录。

- [ ] **Step 4: 实现服务器准备脚本**

`prepare_server.sh` 只创建明确目录，使用 `install -d` 设置 owner/mode，验证：

- `/opt/lyw` 解析后的绝对路径正确。
- `dify_default` 存在。
- `/opt/dify/nginx/conf.d/default.conf.template` 和 `default.conf` 存在。
- 磁盘至少保留 8GB。
- `runtime.env` 最终权限为 600，目录权限为 700。

脚本不得生成或打印真实秘密；数据库密码生成与本机凭据传输由执行阶段单独完成。

- [ ] **Step 5: 实现幂等 Nginx 更新脚本**

`update_dify_nginx.sh install`：

1. 确认模板和生成配置均不含 LYW marker。
2. 备份两个文件并生成校验和。
3. 把片段插入每个文件的 `location / {` 之前。
4. 验证两个文件各恰有一组 marker。
5. 执行容器内 `nginx -t`；失败则恢复备份并再次 `nginx -t`。
6. 上游健康时 reload。

`update_dify_nginx.sh rollback <backup-id>`：只移除 marker 块或恢复明确备份，不删除其他 location。

- [ ] **Step 6: 实现只读服务器验证脚本**

`verify_server.sh` 输出非秘密状态：容器 health/restart/OOM、监听端口、HTTP 状态、内存、Swap、磁盘、Dify 根页面和三个 LYW 入口。脚本不得调用写接口或输出容器环境变量。

- [ ] **Step 7: 运行脚本契约与 shell 语法检查**

Run: `python -m unittest tests.scripts.test_deployment_scripts_contract -v`

Run: `bash -n scripts/deploy/prepare_server.sh scripts/deploy/update_dify_nginx.sh scripts/deploy/verify_server.sh`

Expected: 全部 PASS。

- [ ] **Step 8: 检查 diff 并记录建议提交**

Suggested commit: `ops: add repeatable deployment workflow`

Do not commit without explicit authorization and a safe branch.

---

### Task 6: 执行本地总验证并生成发布包

**Files:**
- Read: all files changed in Tasks 1–5
- Create outside repo: an explicit temporary release directory

**Interfaces:**
- Consumes: `scripts/deploy/build_release.ps1`
- Produces: versioned release archive and SHA-256 manifest

- [ ] **Step 1: 检查工作区并隔离用户原有改动**

Run: `git status --short --branch`

Expected: 明确列出任务前已存在的 `CONTEXT.md` 修改和相关未跟踪文档；不得覆盖、暂存或提交这些文件。

- [ ] **Step 2: 运行所有 Python 部署契约测试**

Run: `python -m unittest discover -s tests/scripts -p 'test_deployment_*_contract.py' -v`

Expected: PASS，0 failures。

- [ ] **Step 3: 运行后端全量测试与 deployment 构建**

Run: `business\mvnw.cmd -pl business -Pdeployment clean package`

Expected: BUILD SUCCESS，0 failures，0 errors。

- [ ] **Step 4: 运行用户端全量测试和两个前端构建**

Run: `npm test` and `npm run build` in `web`。

Run: `npm run build` in `admin`。

Expected: 测试与构建全部退出 0。

- [ ] **Step 5: 运行仓库与 Jar 秘密扫描**

Run: `python scripts/security/scan_repository.py`

Run: Jar deployment contract against the actual artifact.

Expected: 无高危阻塞；本机真实秘密命中数 0。

- [ ] **Step 6: 生成发布包并核对内容**

发布标识使用 `YYYYMMDD-HHMMSS-<short-head>`；detached HEAD 仍可使用当前 commit 短 OID。发布包不得包含 `.git`、`.idea`、`node_modules`、本机配置、私钥、日志、上传目录或数据库文件。

- [ ] **Step 7: 记录发布清单，不提交 Git**

记录 artifact 名称、大小和 SHA-256。不得把 release archive 放入仓库。

---

### Task 7: 服务器预检、目录与秘密注入

**Files on server:**
- Create: `/opt/lyw/releases/<release-id>`
- Create: `/opt/lyw/data/{mysql,mongo,redis,uploads}`
- Create: `/opt/lyw/secrets/runtime.env`
- Create: `/opt/lyw/backups`

**Interfaces:**
- Consumes: approved SSH key `codex_lyw_deploy`
- Produces: root-owned runtime configuration

- [ ] **Step 1: 重新验证目标服务器身份与 Dify 基线**

Run over SSH: `hostname`, `uname`, Docker Compose project list, `dify_default` existence, Dify HTTP 307/200, Dify worker restart counts 0, `/opt/dify/.env` 中 `LOG_LEVEL=INFO`，以及 `/opt/dify/.env.codex-backup-20260830-1026` 存在。

Expected: hostname `VM-0-11-ubuntu`，实例 IP `42.193.184.77`，Dify 健康。

- [ ] **Step 2: 运行服务器准备脚本**

上传并执行 `prepare_server.sh`。Expected: 只创建 `/opt/lyw` 子目录；磁盘、网络、Dify 配置检查通过。

- [ ] **Step 3: 在服务器生成数据库秘密**

以 `umask 077` 生成独立的 MySQL root/app、Mongo root/app、Redis 密码和 JWT fallback。值直接写入 root-owned 临时 env，不输出到 stdout。若已有 `runtime.env`，停止并要求明确更新授权，不覆盖。

- [ ] **Step 4: 从本机生成外部服务 env 临时文件**

脚本读取本机忽略配置并映射到 env 键；只输出键名完整性，不输出值。临时文件必须位于显式临时目录且被 Git 忽略。

- [ ] **Step 5: 通过 SCP 传输并在服务器合并**

传输到 `/opt/lyw/secrets/runtime.env.incoming`，服务器 root 合并后设置 owner `root:root`、mode 600，随后精确删除 incoming 和本机临时文件。回查两个位置确认无残留。

- [ ] **Step 6: 验证秘密文件而不输出值**

只报告必填键的 `SET/MISSING`、文件 owner/mode 和重复键数量。Expected: 必填键全部 SET、mode 600、重复键 0。

---

### Task 8: 上传发布包并启动独立数据栈

**Files on server:**
- Populate: `/opt/lyw/releases/<release-id>`
- Create: `/opt/lyw/compose.yaml`
- Create symlink after validation: `/opt/lyw/current`

- [ ] **Step 1: 上传到 release 临时目录并核对 SHA-256**

临时目录必须为 `/opt/lyw/releases/.incoming-<release-id>`。校验全部通过后用同一文件系统原子重命名为 `<release-id>`；失败则精确删除该 incoming 目录。

- [ ] **Step 2: 拉取固定版本镜像并记录 digest**

Run: `docker compose pull`。记录每个镜像的 repository digest；不得输出容器环境变量。

- [ ] **Step 3: 在服务器验证 Compose 展开结果**

Run: `docker compose --env-file /opt/lyw/secrets/runtime.env config --quiet`

由于 `config` 完整输出会展开秘密，只使用 `--quiet`，不得把展开配置打印到工具输出。

- [ ] **Step 4: 先启动 MySQL、MongoDB 和 Redis**

Run: `docker compose up -d lyw-mysql lyw-mongo lyw-redis`

Expected: 三个容器健康，restart count 0，OOM false。

- [ ] **Step 5: 验证全新 MySQL 初始化**

通过容器内客户端使用环境变量认证，只输出数据库名、表数量、`post_category` 行数和编码列表；不输出密码或用户数据。Expected: `travel_share` 存在，表与正式分类完整。

- [ ] **Step 6: 验证数据库未开放公网**

Run: `ss -lntup` and `docker port` for the three database containers.

Expected: 无宿主机 3306、27017、6379 监听。

---

### Task 9: 启动后端与前端并完成内部健康验证

- [ ] **Step 1: 构建仅复制成品的运行镜像**

Run: `docker compose build lyw-backend lyw-frontend`

Expected: 构建过程不运行 Maven/npm，不出现秘密值，生成镜像 digest 可记录。

- [ ] **Step 2: 启动后端**

Run: `docker compose up -d lyw-backend`

轮询 health 条件，不使用固定长时间 sleep。Expected: healthy、restart 0、OOM false。

- [ ] **Step 3: 验证内部分类接口与上传目录**

从 `dify-nginx-1` 或同网络临时 curl 容器请求 `http://lyw-backend:8080/lyw/web/post/categories`。Expected: HTTP 200，返回正式分类；不创建数据。

检查后端内 `/data/uploads` 可写、宿主机绑定路径正确，但不创建持久测试文件；使用临时文件时必须在同一步精确删除并回查。

- [ ] **Step 4: 启动前端并验证内部子路径**

Run: `docker compose up -d lyw-frontend`

从 Dify 网络请求 `/travel/`、一个用户端深链接、`/travel-admin/` 和一个管理端深链接。Expected: 均返回对应 `index.html`，资源路径前缀正确。

- [ ] **Step 5: 记录启动资源快照**

记录所有 LYW 容器 CPU、内存、PIDs、restart、OOM，宿主机 free/swap/load 和 Dify 容器状态，作为接入公网前基线。

---

### Task 10: 把三个入口接入 Dify Nginx

- [ ] **Step 1: 再次确认上游和 Dify 健康**

只有 Task 9 全部通过、Dify 根页面可用、两个 Dify Celery restart 为 0 时继续。

- [ ] **Step 2: 执行 Nginx install 脚本**

Run: `sudo /opt/lyw/current/scripts/deploy/update_dify_nginx.sh install`

Expected: 模板和生成配置各恰有一组 LYW marker；备份和 SHA-256 写入 `/opt/lyw/backups/<backup-id>`；`nginx -t` PASS；reload 成功。

- [ ] **Step 3: 验证公网路由**

从服务器本机和开发机分别请求：

- `http://42.193.184.77/` → Dify 正常重定向或页面。
- `http://42.193.184.77/travel/` → 用户端 200。
- 用户端深链接 → 200。
- `http://42.193.184.77/travel-admin/` → 管理端 200。
- 管理端深链接 → 200。
- `http://42.193.184.77/business/lyw/web/post/categories` → 200 和正式分类。

- [ ] **Step 4: 验证浏览器关键读流程**

检查用户端发现页、登录验证码显示、管理端登录页、分类加载和静态图片路径。不得注册用户、登录真实账号、发布帖子或上传图片。

- [ ] **Step 5: 如任一入口失败则立即回滚 Nginx**

Run: `update_dify_nginx.sh rollback <backup-id>`，随后 `nginx -t` 和 reload。回滚后验证 Dify 根页面恢复，再诊断 LYW，不让错误路由持续在线。

---

### Task 11: 资源观察、最终验证与交付

- [ ] **Step 1: 运行完整服务器验证脚本**

Run: `/opt/lyw/current/scripts/deploy/verify_server.sh`

Expected: LYW 全部 healthy/restart 0/OOM false；Dify 状态不回退；数据库无公网监听；HTTP 契约全部通过。

- [ ] **Step 2: 进行条件式观察**

至少采集三个间隔快照，每次根据容器 restart/OOM、可用内存、Swap 增量和 HTTP 状态决定是否继续。不得只等待固定时长后假定稳定。

停止条件：任一 LYW 或 Dify 容器 OOM、持续重启、Dify HTTP 失败、Swap 持续快速增长或系统负载持续影响请求。

- [ ] **Step 3: 达到停止条件时执行安全降级**

先回滚 LYW Nginx marker，再 `docker compose stop` LYW 容器；保留 `/opt/lyw/data`、release、备份和日志。验证 Dify 恢复后，把状态报告为“资源不足，部署未完成”，不得报告成功。

- [ ] **Step 4: 未触发停止条件时执行本地最终验证**

重新运行后端测试、用户端测试、两个生产构建、部署契约和安全扫描，读取完整输出。只有全部退出 0 才可声明代码与构建通过。

- [ ] **Step 5: 检查工作区最终 diff**

Run: `git status --short --branch` and focused `git diff`。确认未改动用户任务前已有文件，未加入秘密、构建产物、私钥、release archive 或临时文件。

- [ ] **Step 6: 清理本次临时文件**

删除并回查本机构建临时目录、秘密 incoming 文件和服务器 `.incoming-*`。不删除正式 release、数据、备份或 SSH key。

- [ ] **Step 7: 交付部署结果**

报告三个 URL、release ID、镜像 digest、数据库初始化数量、容器健康/资源状态、Dify 验证、Nginx 备份 ID、自动化测试数量、已知警告与回滚命令。

- [ ] **Step 8: 单独请求 SSH 密钥解绑授权**

部署密钥 `codex_lyw_deploy` 是持久访问权限。部署完成后询问用户是否解绑并删除腾讯云密钥；在获得明确授权前保留，不自动解绑或删除本机私钥。获得授权后先解绑实例，再删除腾讯云密钥记录和本机两份密钥文件，并验证密码登录策略按腾讯云规则恢复。

Suggested final commits if later authorized and on a safe branch:

1. `feat: make upload storage configurable`
2. `feat: support prefixed production routes`
3. `build: add secret-free deployment profile`
4. `ops: add constrained docker deployment`
5. `ops: add repeatable deployment workflow`

No commit, push, PR, merge, or worktree cleanup is authorized by this plan.

# 仓库密钥与敏感信息历史清理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 清理 `liao01/001biyee` 当前版本和全部远端 Git 历史中的密钥、个人数据与运行期文件，并建立可重复执行的防回流验证。

**Architecture:** 当前脏工作区只承载安全代码改造和审阅文档；远端历史改写在 `C:\tmp` 下的独立镜像完成。仓库内新增不输出秘密原文的 Python 扫描器作为统一验证入口，历史改写使用 `git-filter-repo`，远端更新前后分别扫描全部引用并校验引用没有并发变化。

**Tech Stack:** Git、GitHub、Python 3 标准库、PowerShell、Java 17、Spring Boot、Maven、Vue/Vite、`git-filter-repo`

## Global Constraints

- 不覆盖、清理或提交当前工作区中已有的非本任务改动。
- 测试文件统一放在 `tests/`，并镜像被测脚本的目录结构。
- 扫描报告只输出规则编号、文件路径、Git 引用和命中数量，不输出秘密原文。
- 数据库脚本只保留 DDL、索引和约束，不保留任何真实 `INSERT` 数据。
- 运行时凭据的唯一正式事实源是环境变量；仓库只保存变量名和无效示例值。
- 历史改写必须覆盖所有远端分支和标签，并在强制推送前验证远端引用未发生变化。
- 任何关键验证失败均停止远端推送。
- 历史清理不能代替凭据吊销和轮换。

---

## 文件结构

- Create: `.gitignore` — 仓库级运行期文件和敏感配置忽略规则。
- Create: `.env.example` — 后端所需环境变量的无敏感值示例。
- Create: `SECURITY.md` — 凭据存储、泄露响应和历史清理后的协作规则。
- Create: `scripts/security/scan_repository.py` — 当前树、指定目录或全部 Git 引用的敏感信息扫描器。
- Create: `tests/scripts/security/test_scan_repository.py` — 扫描规则、脱敏输出和退出码测试。
- Create: `scripts/security/sanitize_sql.py` — 将 SQL 导出收敛为只含结构的确定性转换器。
- Create: `tests/scripts/security/test_sanitize_sql.py` — DDL 保留、数据删除和个人信息删除测试。
- Modify: `business/src/main/resources/application.properties` — 将数据库、Redis、AI 和 MongoDB 配置切换为环境变量。
- Modify: `business/src/main/resources/application.yml` — 将 RAG 与地图配置切换为环境变量。
- Modify: `business/src/main/java/com/jiawa/lyw/Util/MailUtils.java` — 删除硬编码邮箱账号、授权码和个人测试入口。
- Modify: `business/src/main/java/com/jiawa/lyw/Util/JwtUtil.java` — 从环境读取 JWT 密钥并停止记录令牌。
- Modify: `http/*.http` — 删除历史令牌和个人测试数据，改用客户端环境变量占位符。
- Delete from version control: `business/log/**`, `log/**`, `uploads/**` — 运行期日志和用户上传内容。
- Replace: `sql/travel_share.sql` — 只保留结构；若无法证明转换完整则从版本库删除并改为独立迁移脚本。
- Delete from history: 所有日志、上传文件、带数据 SQL 导出和已确认不应存在的敏感配置快照。

### Task 1: 建立敏感信息扫描事实源

**Files:**
- Create: `scripts/security/scan_repository.py`
- Create: `tests/scripts/security/test_scan_repository.py`

**Interfaces:**
- Consumes: 目录路径或 Git 仓库路径、可选 `--all-refs`。
- Produces: `scan_paths(paths: list[Path]) -> list[Finding]`、`scan_git_refs(repo: Path) -> list[Finding]`；进程退出码 `0` 表示无命中，`1` 表示存在命中，`2` 表示扫描失败。

- [ ] **Step 1: 编写失败测试**

覆盖以下行为：

```python
def test_detects_secret_without_printing_value():
    candidate_value = "sk-" + "example-secret-value-123456"
    sample = f"service.api-key={candidate_value}"
    findings = scan_text(Path("application.properties"), sample)
    assert findings[0].rule_id == "generic-secret-assignment"
    assert "sk-example" not in findings[0].summary

def test_ignores_environment_placeholder():
    findings = scan_text(
        Path("application.properties"),
        "service.api-key=${SERVICE_API_KEY}",
    )
    assert findings == []

def test_detects_sql_personal_data_and_insert():
    phone_number = "188" + "00000000"
    findings = scan_text(
        Path("dump.sql"),
        f"INSERT INTO member VALUES (1, '{phone_number}');",
    )
    assert {item.rule_id for item in findings} >= {"sql-data-row", "phone-number"}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `python -m unittest tests.scripts.security.test_scan_repository -v`  
Expected: FAIL，原因是 `scripts.security.scan_repository` 尚不存在。

- [ ] **Step 3: 实现最小扫描器**

实现：

- 凭据赋值、常见令牌、私钥头、JWT、手机号、邮箱、密码哈希和 SQL 数据行规则；
- `.git`、依赖、构建目录和二进制文件排除；
- 只输出规则编号、相对路径、行号和数量；
- `--all-refs` 使用 `git rev-list --objects --all` 和 `git cat-file` 扫描所有可达 blob；
- 对允许的 `${ENV_NAME}`、`{{ENV_NAME}}` 和明显无效示例值设置窄范围白名单。

- [ ] **Step 4: 运行单元测试**

Run: `python -m unittest tests.scripts.security.test_scan_repository -v`  
Expected: PASS。

- [ ] **Step 5: 对当前工作区执行基线扫描**

Run: `python scripts/security/scan_repository.py . --report C:\tmp\001biyee-baseline.json`  
Expected: exit `1`，报告存在命中但不包含任何秘密原文。

- [ ] **Step 6: 提交扫描器**

```powershell
git add -- scripts/security/scan_repository.py tests/scripts/security/test_scan_repository.py
git commit -m "test: add repository sensitive data scanner"
```

### Task 2: 收敛运行配置和源码中的凭据

**Files:**
- Create: `.env.example`
- Modify: `business/src/main/resources/application.properties`
- Modify: `business/src/main/resources/application.yml`
- Modify: `business/src/main/java/com/jiawa/lyw/Util/MailUtils.java`
- Modify: `business/src/main/java/com/jiawa/lyw/Util/JwtUtil.java`
- Modify: `business/src/test/java/com/jiawa/lyw/BusinessApplicationTests.java`
- Test: `tests/scripts/security/test_scan_repository.py`

**Interfaces:**
- Consumes: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `DEEPSEEK_BASE_URL`, `DEEPSEEK_API_KEY`, `DASHSCOPE_API_KEY`, `MONGODB_URI`, `RAG_URL`, `RAG_WORKSPACE_SLUG`, `RAG_API_KEY`, `AMAP_API_KEY`, `MAIL_USERNAME`, `MAIL_AUTH_CODE`, `JWT_SECRET`。
- Produces: 无硬编码秘密的 Spring 配置；`MailUtils` 和 `JwtUtil` 从环境读取凭据。

- [ ] **Step 1: 扩充失败测试**

增加配置扫描测试，要求真实形式赋值失败、`${ENV_NAME}` 通过，并要求 `JwtUtil.java` 不包含固定 `key` 字符串或令牌日志模板。

- [ ] **Step 2: 运行扫描测试确认失败**

Run: `python -m unittest tests.scripts.security.test_scan_repository -v`  
Expected: FAIL，现有配置和源码仍有命中。

- [ ] **Step 3: 修改 Spring 配置**

使用以下形式：

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.data.redis.password=${REDIS_PASSWORD:}
langchain4j.open-ai.chat-model.api-key=${DEEPSEEK_API_KEY}
langchain4j.community.dashscope.chat-model.api-key=${DASHSCOPE_API_KEY}
spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/db_chat_memory}
```

```yaml
rag:
  server:
    url: ${RAG_URL}
    slug: ${RAG_WORKSPACE_SLUG}
    auth:
      apikey: ${RAG_API_KEY}
amap:
  key: ${AMAP_API_KEY}
```

- [ ] **Step 4: 修改邮件与 JWT 工具**

`MailUtils` 从 `MAIL_USERNAME`、`MAIL_AUTH_CODE` 读取配置，删除 `main` 中的真实收件人测试。`JwtUtil` 从 `JWT_SECRET` 读取至少 32 字节的密钥，启动或首次使用时缺失则明确失败；日志不再记录 token、payload 中的个人资料或完整会话内容。

- [ ] **Step 5: 添加无效示例配置**

`.env.example` 只列出变量名，例如：

```dotenv
DB_URL=jdbc:mysql://127.0.0.1:3306/travel_share
DB_USERNAME=change-me
DB_PASSWORD=change-me
JWT_SECRET=replace-with-at-least-32-random-bytes
```

其他接口密钥统一使用 `change-me`，不得复制历史值。

- [ ] **Step 6: 执行测试和构建**

Run: `python -m unittest tests.scripts.security.test_scan_repository -v`  
Expected: PASS。

Run: `mvn -pl business test`  
Expected: BUILD SUCCESS；测试环境通过专用无效变量或测试配置启动，不连接生产服务。

- [ ] **Step 7: 提交配置改造**

```powershell
git add -- .env.example business/src/main/resources/application.properties business/src/main/resources/application.yml business/src/main/java/com/jiawa/lyw/Util/MailUtils.java business/src/main/java/com/jiawa/lyw/Util/JwtUtil.java business/src/test/java/com/jiawa/lyw/BusinessApplicationTests.java tests/scripts/security/test_scan_repository.py
git commit -m "fix: move runtime credentials to environment"
```

### Task 3: 清理数据库数据、请求样例和运行期文件

**Files:**
- Create: `scripts/security/sanitize_sql.py`
- Create: `tests/scripts/security/test_sanitize_sql.py`
- Modify: `http/*.http`
- Replace: `sql/travel_share.sql`
- Delete from version control: `business/log/**`, `log/**`, `uploads/**`

**Interfaces:**
- Consumes: SQL 文本。
- Produces: `sanitize_sql(text: str) -> str`，保留 DDL、索引和约束，删除数据行及导出环境元数据。

- [ ] **Step 1: 编写 SQL 转换失败测试**

```python
def test_preserves_schema_and_removes_rows():
    phone_number = "188" + "00000000"
    source = f"""
    CREATE TABLE member (id bigint, mobile varchar(20));
    INSERT INTO member VALUES (1, '{phone_number}');
    """
    result = sanitize_sql(source)
    assert "CREATE TABLE member" in result
    assert "INSERT INTO" not in result
    assert phone_number not in result
```

同时覆盖多行 `INSERT`、注释中的服务器元数据、邮箱、验证码和哈希值。

- [ ] **Step 2: 运行测试确认失败**

Run: `python -m unittest tests.scripts.security.test_sanitize_sql -v`  
Expected: FAIL，转换器尚不存在。

- [ ] **Step 3: 实现确定性 SQL 转换**

解析以分号结束的语句，仅保留允许的 DDL 集合：`CREATE TABLE`、`ALTER TABLE`、`CREATE INDEX`、`DROP TABLE IF EXISTS`、字符集设置和外键开关。遇到无法分类的语句时以 exit `2` 失败，不静默保留。

- [ ] **Step 4: 生成结构脚本并复扫**

Run: `python scripts/security/sanitize_sql.py sql/travel_share.sql --output C:\tmp\travel_share.schema.sql`  
Expected: exit `0`。

将经扫描通过的输出替换 `sql/travel_share.sql`；如果转换器因未知语句失败，则删除该导出并从现有 Mapper/迁移来源另建结构脚本，不放宽规则。

- [ ] **Step 5: 脱敏 HTTP 请求样例**

将 token、手机号、邮箱、验证码、密码和用户 ID 改为客户端环境变量，例如：

```http
token: {{auth_token}}

{
  "loginName": "{{example_login_name}}",
  "password": "{{example_password}}"
}
```

- [ ] **Step 6: 从索引移除运行期文件**

使用明确路径执行：

```powershell
git rm -r --cached -- business/log log uploads
```

该命令只停止版本控制，不删除当前磁盘文件。

- [ ] **Step 7: 运行完整扫描与测试**

Run: `python -m unittest discover -s tests -v`  
Expected: PASS。

Run: `python scripts/security/scan_repository.py .`  
Expected: exit `0`。

- [ ] **Step 8: 提交数据清理**

```powershell
git add -- scripts/security/sanitize_sql.py tests/scripts/security/test_sanitize_sql.py sql/travel_share.sql http
git commit -m "fix: remove sensitive runtime and database data"
```

### Task 4: 建立防回流规则和协作说明

**Files:**
- Create: `.gitignore`
- Create: `SECURITY.md`
- Modify: `tests/scripts/security/test_scan_repository.py`

**Interfaces:**
- Consumes: 新提交文件集合。
- Produces: 统一忽略规则、安全提交流程和可在 CI/本地执行的扫描命令。

- [ ] **Step 1: 编写忽略规则失败测试**

测试根 `.gitignore` 必须覆盖：

```text
.env
.env.*
!.env.example
*.log
log/
logs/
business/log/
uploads/
*.p12
*.pfx
*.pem
*.key
*.jks
*.keystore
```

- [ ] **Step 2: 运行测试确认失败**

Run: `python -m unittest tests.scripts.security.test_scan_repository -v`  
Expected: FAIL，根忽略规则尚不存在。

- [ ] **Step 3: 创建根忽略规则**

合并各子项目已有规则，不删除仍有意义的生态规则；确保 `.env.example` 可提交。

- [ ] **Step 4: 编写安全说明**

`SECURITY.md` 记录：

- 凭据只进入环境变量；
- 提交前运行 `python scripts/security/scan_repository.py .`；
- 发现泄露后先吊销，再清理历史；
- 历史改写后协作者必须重新克隆，禁止把旧分支合回；
- 报告问题时不得把密钥粘贴到 Issue、PR 或聊天。

- [ ] **Step 5: 运行测试并提交**

Run: `python -m unittest discover -s tests -v`  
Expected: PASS。

```powershell
git add -- .gitignore SECURITY.md tests/scripts/security/test_scan_repository.py
git commit -m "chore: prevent sensitive files from returning"
```

### Task 5: 构造并验证独立历史净化镜像

**Files:**
- Create outside repository: `C:\tmp\001biyee-cleanup-20260726\` — 临时镜像、引用快照和脱敏扫描报告。
- Create outside repository: `C:\tmp\001biyee-cleanup-20260726\replace-text.txt` — 仅在受限临时目录保存的替换规则，完成后删除。

**Interfaces:**
- Consumes: GitHub 远端全部引用、已确认的安全当前树、已识别敏感值指纹。
- Produces: 全部引用已改写且扫描通过的 bare 镜像。

- [ ] **Step 1: 检查工具与认证**

Run: `git filter-repo --version`  
Expected: 返回版本；若缺失，申请安装 `git-filter-repo`，不得退回已弃用且难验证的 `git filter-branch`。

Run: `gh auth status`  
Expected: 当前账号对 `liao01/001biyee` 具有推送权限；输出不得记录令牌。

- [ ] **Step 2: 创建受限临时目录和远端引用快照**

使用 `New-Item` 创建明确的时间戳目录，执行：

```powershell
git ls-remote --heads --tags https://github.com/liao01/001biyee.git
```

保存引用名和 SHA，不保存仓库文件内容。

- [ ] **Step 3: 镜像克隆全部引用**

Run: `git clone --mirror https://github.com/liao01/001biyee.git C:\tmp\001biyee-cleanup-20260726\mirror.git`  
Expected: exit `0`，引用数量与快照一致。

- [ ] **Step 4: 执行路径级历史删除**

使用 `git filter-repo --invert-paths` 删除所有历史中的：

- `business/log/`
- `log/`
- `uploads/`
- 含真实数据的历史 SQL 导出路径
- 已确认只承载真实秘密且不应保留的旧配置副本

命令使用明确的多个 `--path`，不使用通配删除工作区根目录。

- [ ] **Step 5: 执行文本替换**

从本地已确认值生成 `replace-text.txt`，每行使用 `literal:<secret>==><REMOVED_SECRET>`；生成和执行过程不得回显文件。用 `git filter-repo --replace-text` 覆盖仍需保留的源码和配置历史。

- [ ] **Step 6: 把安全当前树接到净化后的默认分支**

将 Tasks 1–4 的安全提交移植到净化镜像对应默认分支，确保远端当前版本包含扫描器、环境变量改造、结构 SQL、忽略规则和安全说明，不携带当前工作区其他未提交文件。

- [ ] **Step 7: 回收旧对象**

删除 `refs/original/` 和 filter-repo 生成的备份引用，执行：

```powershell
git reflog expire --expire=now --all
git gc --prune=now --aggressive
```

仅在独立临时镜像中执行。

- [ ] **Step 8: 扫描全部改写引用**

Run: `python scripts/security/scan_repository.py C:\tmp\001biyee-cleanup-20260726\mirror.git --all-refs --report C:\tmp\001biyee-cleanup-20260726\post-rewrite.json`  
Expected: exit `0`。

并验证：

- 禁止路径在 `git rev-list --objects --all` 中零命中；
- 所有 heads/tags 可解析；
- 已知秘密的哈希指纹零命中；
- 报告不含秘密原文。

### Task 6: 最终构建、远端并发校验和强制更新

**Files:**
- No repository file changes.

**Interfaces:**
- Consumes: 已验证镜像、初始远端引用快照。
- Produces: 已净化的 GitHub 分支和标签，以及推送后独立复验结果。

- [ ] **Step 1: 从净化镜像创建临时非 bare 检出**

检出默认分支，设置无效测试环境变量，执行：

Run: `mvn -pl business test`  
Expected: BUILD SUCCESS。

Run: `npm --prefix admin run build`  
Expected: build 成功。

Run: `npm --prefix web run build`  
Expected: build 成功。

- [ ] **Step 2: 再次读取远端引用**

Run: `git ls-remote --heads --tags https://github.com/liao01/001biyee.git`  
Expected: 与 Task 5 Step 2 快照完全一致；如不一致，停止推送并重新执行历史净化。

- [ ] **Step 3: 强制更新全部受影响引用**

逐个 heads/tags 使用明确 refspec 和旧 SHA 租约推送，避免无条件 `--force --mirror` 覆盖扫描后新增的远端工作。每个引用只在旧 SHA 与快照一致时更新。

- [ ] **Step 4: 删除远端已废弃引用**

仅删除设计范围内、已确认不应继续存在的远端引用；如存在未识别分支，不删除，先停止并报告。

- [ ] **Step 5: 从 GitHub 重新镜像克隆并复验**

创建新的临时目录重新执行：

```powershell
git clone --mirror https://github.com/liao01/001biyee.git C:\tmp\001biyee-verify-20260726\mirror.git
```

Run: `python scripts/security/scan_repository.py C:\tmp\001biyee-verify-20260726\mirror.git --all-refs`  
Expected: exit `0`。

- [ ] **Step 6: 输出不含秘密原文的结果**

报告：

- 改写的分支和标签数量；
- 删除的路径类别和 blob 数量；
- 各扫描规则最终命中数均为零；
- 新默认分支 SHA；
- 必须轮换的凭据类别；
- 协作者必须重新克隆且不得合并旧分支。

### Task 7: 本地工作区安全交接

**Files:**
- No automatic deletions.

**Interfaces:**
- Consumes: 当前工作区状态、全新净化克隆。
- Produces: 不含秘密的本地改动迁移建议和旧副本处置边界。

- [ ] **Step 1: 重新扫描当前未提交改动**

Run: `git diff --binary` 的内容只通过扫描器处理，不打印；未跟踪文件按同一规则扫描。

- [ ] **Step 2: 生成非敏感改动清单**

只列出可迁移文件路径和状态，不生成包含秘密的补丁文件。

- [ ] **Step 3: 明确旧本地仓库风险**

告知用户当前 `.git` 仍包含旧对象，不能继续推送或把旧分支合并回净化仓库。

- [ ] **Step 4: 等待用户确认后再迁移或删除旧副本**

迁移和删除属于后续独立授权动作；本任务不自动删除当前工作区。

## 计划自检

- 设计中的当前版本清理、全历史改写、全部引用验证、远端并发保护、凭据轮换和旧克隆处置均有对应任务。
- 所有测试均位于 `tests/` 并镜像 `scripts/security/`。
- 扫描、转换和推送失败都有明确停止条件。
- 没有使用未定义的占位实现；时间戳目录在执行时由明确路径创建并在操作前解析确认。
- 不把任何秘密原文写入计划、报告、提交信息或用户输出。

# 邮箱身份 HTTP 与数据链路

本记录覆盖 #15 的新增邮箱链路与 #16 的前后端切换实现。真实隔离 HTTP、MySQL 和 Vue 表单联调已通过；生产 HTTPS、浏览器引擎完整旅程及远端发布门禁仍须单独验收。

## 正式事实源

- HTTP 路径、请求和响应：[IdentityController.java](../../business/src/main/java/com/jiawa/lyw/identity/api/IdentityController.java)。接口统一返回 `CommonResp`，所有身份响应禁止缓存；刷新令牌只放在安全 Cookie 中，不进入 JSON。
- 应用事务：[DefaultIdentityApplicationService.java](../../business/src/main/java/com/jiawa/lyw/identity/application/DefaultIdentityApplicationService.java)。模块外调用正式应用接口，不读取内部 Mapper。
- 密码规则：[PasswordPolicy.java](../../business/src/main/java/com/jiawa/lyw/identity/domain/PasswordPolicy.java)；状态及凭据结构：[MemberAccount.java](../../business/src/main/java/com/jiawa/lyw/identity/domain/MemberAccount.java)。本说明不另行维护字段枚举和长度限制。
- 默认有效期与配置：[IdentityConfiguration.java](../../business/src/main/java/com/jiawa/lyw/identity/infrastructure/IdentityConfiguration.java)；数据库结构与升级：[identity-migration.md](identity-migration.md)。
- 可执行行为契约：[IdentityHttpIT.java](../../business/src/test/java/com/jiawa/lyw/identity/api/IdentityHttpIT.java)。

## 数据变化

注册先规范化邮箱并执行统一密码规则；新账户只写正式邮箱和 BCrypt 凭据，不生成历史手机号或旧密码。待验证账户再次注册时，新密码与新链接一同替换，旧链接失效；已验证及不可登录状态的账户不会被匿名注册改写。

注册通过唯一键原子初始化账户，再锁定正式账户行；注册和重置申请使用明确的读已提交事务隔离，避免不存在令牌区间上的间隙锁死锁。已有账户的并发修改仍由账户行锁串行化，不能用无锁先查后插替代。

验证链接与重置链接只通过邮件交付。原文不写数据库和应用日志；持久化保存摘要、用途、所属账户及有效期。消费链接时先锁账户，再以用途、邮箱、未使用和未过期条件更新。用途错误、过期或重复消费均不能改变账户。

首次成功登录旧邮箱账户时，旧算法只用于兼容验证，随后在登录事务内升级正式凭据并清除旧密码副本。刷新会话轮换在同一事务中撤销旧摘要并创建新摘要；同一 Cookie 的并发刷新只能有一个成功。退出重复调用安全，撤销服务端会话并清除浏览器 Cookie。

密码重置成功时，更新凭据并撤销该账户全部刷新会话。已有短期访问令牌不会被改写；后续访问边界由 #16 的验证器负责。重置申请对不存在、不可重置和可重置邮箱返回相同公开结果。邮件发送失败会回滚令牌变化，原有已投递链接仍可用；日志只记录固定失败信息，不包含邮箱、令牌或提供商异常。

## 运行配置与发布条件

### #16 后端访问边界

身份读取接口继续以 `IdentityController` 为正式 HTTP 契约；当前会员来源为 [CurrentMemberProvider.java](../../business/src/main/java/com/jiawa/lyw/identity/application/CurrentMemberProvider.java)，模块外只使用该接口。HTTP 实现在请求内验证标准 Bearer 头和正式数据库账户，并缓存本次请求的最小会员信息。响应仅包含展示身份，不返回密码、凭据算法或令牌。重复 Authorization 头和历史 token 头不能用于会员鉴权。

访问令牌验证和生命周期以 [JwtAccessTokenService.java](../../business/src/main/java/com/jiawa/lyw/identity/infrastructure/JwtAccessTokenService.java) 及身份配置为准；每次请求都核对账户仍具备访问资格。重置密码和退出会撤销刷新会话，但不会立即撤销已签发且尚未过期的访问令牌；封禁账户通过正式账户检查阻止后续访问。

旧会员注册、登录、重置及短信发送路径已从 HTTP 层移除；旧短信控制器只保留为不注册的兼容代码，不允许重新挂接。可执行下线清单位于 [LegacyIdentityRoutesTests.java](../../business/src/test/java/com/jiawa/lyw/identity/api/LegacyIdentityRoutesTests.java)，不在本文维护第二份路径列表。

历史业务通过 `WebLoginInterceptor` 将已验证会员 ID 桥接至旧线程上下文，不复制令牌；结束请求时清除上下文。心跳通过会员 ID 更新原活动统计表，不再写入原始访问令牌，更新到的历史记录会清空旧令牌字段；这不是对全部历史数据的清理迁移。Redis 日活统计是非权威缓存，不可用时不改变身份校验结果。

管理员身份隔离的本地证据、修复和未完成门禁见 [身份边界安全记录](../security/2026-08-31-identity-token-boundary.md)。

### #16 前端数据链路

[identityHttp.js](../../web/src/modules/identity/identityHttp.js) 是前端邮箱身份 HTTP 的唯一适配器，使用独立传输处理邮箱用例，避免身份请求被自动刷新拦截器再次重试。注册和登录只传正式邮箱及原始密码，不执行客户端摘要；密码复杂度继续由后端正式规则负责，前端只额外校验确认密码一致。

[identitySession.js](../../web/src/modules/identity/identitySession.js) 管理仅存在于内存中的访问令牌。应用启动先通过 Cookie 恢复身份，再挂载业务页面；获取当前会员后，Vuex 只接收最小展示身份。旧浏览器会员缓存只清理、不恢复；前端不读取刷新 Cookie，也不把凭据写入浏览器存储。

会话拦截器同时服务既有全局 Axios 调用和请求实例，按配置的 API 来源及路径限定凭据发送范围；第三方请求不会自动获得会员凭据或触发刷新。并发未授权响应共用在途刷新，并最多重试一次；失败清空身份。匿名启动失败不强制弹窗，后续受保护访问才提示登录。

退出立即清空内存身份，并等待在途刷新结束后撤销最新 Cookie 会话，防止晚到的刷新结果恢复已退出身份。服务器撤销失败时页面明确提示并保留“重试退出”，不声称已安全撤销。

邮件页面从路由读取一次性链接令牌后移除地址栏中的令牌，只在组件内存中用于提交；重新加载需从原邮件打开。入口文档设置不发送 Referer。该措施不替代 HTTPS、第三方脚本信任和部署端安全检查。

### 部署要求

生产 `APP_PUBLIC_URL` 必须显式配置为实际用户访问的前端基础地址，包含部署子路径（如果存在），不得包含用户名、密码、查询或片段。邮件链接保留该子路径。配置值通过环境变量或 Git 忽略的运行文件注入，不依赖请求 Host 生成链接。

生产刷新 Cookie 强制 `Secure`，需要 HTTPS 入口。本地纯 HTTP 开发可显式设置 `IDENTITY_COOKIE_SECURE=false`，不能据此降低生产配置。SMTP 使用加密连接并验证服务器主机名；邮件凭据仍使用既有环境配置。

本记录不声称已经配置生产 HTTPS、投递真实邮件、迁移共享数据库或部署应用。这些操作需要对应环境授权与验证。

## 本地与 CI 验证

已启动本机 Docker 时，从仓库根运行：

```powershell
python -m scripts.run_backend_integration --containers
```

容器模式忽略 `LYW_MIGRATION_TEST_DSN` 和本机数据库配置，为每个测试类启动独立 MySQL，并启用 [IdentityRedisIT.java](../../business/src/test/java/com/jiawa/lyw/identity/api/IdentityRedisIT.java) 的完整应用测试，额外启动本批 Redis、MongoDB；不迁移业务数据库，不连接 Dify。数据使用 tmpfs，端口仅绑定回环地址，不复用已有容器或持久卷。数据库生命周期与正式 schema 初始化以 [MySqlIntegrationDatabase.java](../../business/src/test/java/com/jiawa/lyw/support/MySqlIntegrationDatabase.java) 为准。需要可用 Docker 引擎，以及首次获取测试镜像和辅助镜像的网络或预置缓存；容器模式不会静默跳过集成测试。

无 Docker 时，仍可显式选择原有预置 MySQL 模式：运行 `python -m scripts.run_backend_integration`。该模式优先使用 `LYW_MIGRATION_TEST_DSN`，否则读取既有 Git 忽略的本地 MySQL 配置，拒绝非回环目标。凭据只通过子进程环境传递，不放在命令参数或输出中。两种模式共用原有 HTTP 与 Vue 联调契约，但预置模式会明确跳过仅容器模式运行的完整应用 Redis 测试，不能把它的结果当作完整容器套件证据。

运行器现包含 `IdentitySocketIT`，因此还要求 Node.js 和已安装的 `web` 锁定依赖（在 `web` 目录执行 `npm ci`）。测试直接运行本地 Vitest 入口，不通过临时下载执行器寻找或安装软件。独立的 `vitest.identity-runtime.config.js` 只由隔离运行器调用，普通 `npm test` 不会静默跳过一项伪装成通过的真实环境用例。

每次测试创建带随机批次标识的 `lyw_identity_http_test_` 隔离库，使用正式空库结构；不修改原业务库。测试通过真实 MVC、拦截器、MyBatis 和 MySQL 事务执行，邮件和时间采用明确的外部边界替身，不发送真实邮件。测试结束精确删除本次数据库并回查不存在。

普通 Maven `test` 不要求 MySQL 或 Docker，也不自动运行 `*IT`。CI 的 MySQL 作业仍为 Python 迁移测试提供独立服务；Java HTTP 集成步骤改用上述容器模式，不能用普通单元测试成功代替集成测试证据。远端 CI 及同镜像容器复验状态见迁移记录。

### 2026-08-31 本地验证记录

- 17 项真实 HTTP/MySQL 集成测试、53 项后端测试、48 项 Python 回归通过。
- 并发注册的死锁及重复键失败均先复现再修复；并发重置申请的死锁同样经过失败与成功两轮验证。
- 实际日志切面参与身份测试，确认密码、邮件链接令牌、访问及刷新令牌原文不进入应用日志。
- 本轮隔离测试数据库已删除并回查，残留为 0；工作树和全部可达 Git 对象秘密扫描均为 0 项。
- 未投递真实邮件、未执行远端 CI、未推送或部署。#16 的前端和鉴权切换仍待实施。

### 2026-08-31 #16 后端阶段验证

- 27 项真实 HTTP/MySQL 集成测试通过；63 项后端测试、48 项 Python 回归通过。
- 旧接口的 5 个路径均返回 404，架构测试禁止新增旧短信服务调用或重新连接旧会员注册、登录、重置方法。
- 访问令牌的过期、账户封禁、签名改动、声明缺失、额外声明、未来生效、非指定算法及重复 Authorization 头均被拒绝；会员令牌不能通过管理员鉴权。
- 日志验证覆盖真实身份读取及登录链路；心跳不写原始访问令牌。隔离测试数据库回查残留为 0，没有修改共享业务数据或写入 Redis。
- 本次意图内差异检查通过；工作树和可达 Git 历史的秘密扫描均为 0 项发现。该扫描不替代完整安全审计。
- 前端仍未切换，当前后端变更不能单独发布；本阶段没有进行浏览器验收、真实邮件投递或远端 CI 验证。

### 2026-08-31 #16 前端阶段验证

- 前端 70 项测试通过，生产构建通过；构建仍有既有大包体积警告，未据此声称性能验收通过。
- 成品扫描未发现旧短信路径、客户端密码摘要调用或短信验证码文案；不再使用的用户端摘要和浏览器会话存储脚本已移除，可从 Git 历史恢复。
- Python 回归 49 项通过，工作树及可达 Git 历史秘密扫描均为 0 项发现。新增合成测试输入按精确文件和值登记，测试证明其他路径及未登记值仍会被检测。
- 应用内浏览器在本地前端验证登录、注册、找回密码的页面切换，以及无令牌重置页面的错误提示和返回链接；桌面与移动宽度的登录布局已采集截图检查，无框架覆盖层或相关控制台错误。
- 本轮浏览器的 API 指向未启动的本地隔离端口，发现页出现预期网络失败提示；未提交真实账户、邮件或密码变更，没有连接共享数据库。前端成功用例使用明确的 HTTP 边界替身，后端真实事务验证见前述记录；两者不等于完整浏览器端到端验收。
- #16 仍待真实本地隔离后端联调、Cookie 跨请求验证和完整验收；未推送、合并、部署或执行远端 CI。

### 2026-08-31 #16 真实运行环境联调

- `IdentitySocketIT` 启动真实 Tomcat，绑定回环地址和随机端口，以正式身份 Controller、拦截器、MyBatis 和独立 MySQL 测试库处理请求。不加载本机业务运行配置，不发送真实邮件，不连接 Redis 或其他外部供应商。
- Java HTTP 客户端自动管理 Cookie，验证注册、未验证登录拒绝、验证链接、登录、身份读取、刷新、退出、重置密码、旧凭据失效和新凭据登录；携带 Cookie 的不可信来源请求仍被拒绝。
- 同一服务器进一步执行实际 Vue 注册、登录、找回及重置组件：不替换 HTTP 适配器，使用真实网络和 jsdom 的 Cookie 传输验证邮箱全流程、内存会话重建后的恢复以及退出后恢复失败。邮件仅在这个临时服务器的测试专用收件箱适配器中可读，相关类只存在于测试源码，不进入生产包。
- 运行结束停止服务器及前端测试子进程、删除精确批次测试库和 Tomcat 临时目录；本轮回查数据库及对应临时目录残留均为 0。
- 后端集成套件共 28 项通过，并执行 1 项真实 Vue/HTTP 全流程用例；后端普通回归 64 项通过。跨域来源修复的证据与部署约束见 [Cookie 跨域记录](../security/2026-08-31-identity-cors.md)。
- 同轮前端 70 项测试、生产构建和 Python 49 项回归通过；工作树及可达 Git 历史秘密扫描均为 0 项发现。
- CI 已加入该运行器所需的锁定前端依赖安装，但没有执行远端 CI。本地 HTTP 的非 Secure Cookie 仅用于隔离测试，生产强制 Secure 的契约另有测试；本记录不将 jsdom 联调等同于 Chromium 的完整 SameSite/HTTPS 策略验证。真实邮件投递、生产部署及浏览器引擎最终验收仍未执行。

### 2026-08-31 CI 同镜像本地复验

Docker 恢复后，已改用独立 MySQL 容器运行完整迁移与身份集成套件。测试全部通过且隔离资源已清理；镜像摘要、批次、测试数量和清理证据见[迁移复验记录](identity-migration.md#2026-08-31-docker-恢复后的隔离复验)。这补充了本地运行环境证据，未改变上述远端及真实浏览器验收边界。

### 2026-08-31 Testcontainers 接入验证

按 #12 的测试要求，将未提供测试 DSN 的 Java 集成测试切换到 Testcontainers 管理的 MySQL。最初不设置 DSN 运行 `IdentitySocketIT`，复现为缺少预置 MySQL 配置而失败；修改后同一真实 HTTP/Vue 旅程通过。命令行帮助与 CI 容器入口也分别经过失败、修复、通过验证。

本轮仅在本机 Docker 创建合成数据：业务测试容器带 `lyw.purpose=identity-integration-test`、独立 `lyw.test-batch` 标签和 `lyw-identity-test-` 名称前缀；运行时检查确认回环端口、tmpfs、内存与 CPU 限制，没有宿主机数据挂载。每个测试结束删除精确隔离数据库并回查，再停止自有容器；Testcontainers 的会话标签用于其辅助清理器，不复用已有项目容器、网络或数据卷。

- 显式 BOM 将 Testcontainers 测试依赖统一至 1.21.4，避免间接 BOM 拉回早期 Docker 客户端；版本选择依据[官方兼容性发布说明](https://github.com/testcontainers/testcontainers-java/releases/tag/1.21.4)。依赖树核对相关模块均为同一版本、全部为 test scope。
- 最终版本下，以无效的预置 DSN 运行 `--containers`，28 项 Java 集成测试和其中执行的 1 项 Vue/真实 HTTP 全流程均通过，证明没有回退连接预置数据库。未改动身份业务代码或断言以迎合容器环境。
- 同轮 Python 回归 50 项、用户端 70 项测试通过。全 Maven reactor 的 deployment clean package 通过，业务模块 64 项、生成器模块 1 项普通测试通过；生产 Jar 检查确认不存在本机 Spring 配置或 Testcontainers/docker-java 测试依赖。
- 结束后按业务标签及最新 Testcontainers 会话 `73ad475d-b4e6-4849-8347-dfdf928b0aaf` 回查，业务/辅助容器与 Tomcat 临时目录均为 0。本轮无新增持久化测试卷；首次下载的 Ryuk 辅助镜像作为框架依赖缓存保留，不含本轮业务数据。
- 上一轮部署栈的 6 个测试卷、2 个测试镜像和临时目录仍受原清理拦截影响，继续以[隔离栈记录](../runbooks/2026-08-31-isolated-stack-verification.md)为准；本轮没有通过 Testcontainers 绕过那项限制。
- 本次只补齐 MySQL 的 Testcontainers 路径；Redis 当前仍验证不可用降级，尚未补齐 Testcontainers Redis、提交后事件或幂等消费者验收。真实浏览器 HTTPS、邮件投递、完整目标拓扑和远端 CI 仍未通过，不据此宣布第 0 阶段完成。

### 2026-08-31 完整应用与 Redis 验证

新增 `IdentityRedisIT` 使用正式生产配置与完整 Spring Boot 应用，通过既有 HTTP 入口验证活跃统计去重，以及 Redis 已连接但暂时不响应时仍可读取身份。测试只替换邮件供应商；MySQL、Redis、MongoDB 都使用独立容器，外部 AI/地图/邮件使用合成配置且不调用，不读取本机业务配置。

最初暂停 Redis 时，身份读取超过测试的 3 秒上限；正式 [application-prod.properties](../../business/src/main/resources/application-prod.properties) 新增明确的 Redis 读超时与连接超时后，同一场景返回正常身份，恢复 Redis 后日活计数仍不重复。本次只修改生产模式配置，不改 Git 忽略的本机运行配置，也不声称统计查询、所有外部依赖或完整性能门禁已通过。

宿主机配置隔离先用不可用的 `SPRING_DATA_REDIS_URL` 复现为活跃统计读取失败，再让测试应用使用去掉系统环境变量和 JVM 属性来源的 Spring 环境。CI 的集成步骤固定提供不可用的宿主 Redis/MySQL 地址，测试必须只连接本批数据组件。测试同时限定并恢复 Tomcat 直接使用的 JVM 目录属性，避免后续 HTTP 测试重建已清理的目录；控制台专用测试日志配置不进入生产包。

本轮中断恢复范围：`lyw.purpose=identity-redis-test` 标签下的 Redis/MongoDB、`lyw.purpose=identity-integration-test` 标签下本轮 MySQL，以及对应 Testcontainers 会话辅助容器。Redis/MongoDB 名称采用 `lyw-identity-redis-test-<批次>-<组件>`，MySQL 沿用上节标识；数据均为 tmpfs。临时目录采用 `lyw-identity-redis-test-` 前缀，位于系统临时目录，仅保存测试 Tomcat 和上传目录。故障注入只暂停本批 Redis，finally 会恢复，然后由测试生命周期关闭应用并清理本批容器和临时目录。

最终完整套件在无效预置 DSN、不可用的宿主 Redis/MySQL 环境变量下通过：29 项 Java 集成测试、其中执行的 1 项真实 Vue/HTTP 用例均成功，无跳过。单独复验还提供了不可用的 MongoDB JVM 配置，实际应用仍使用测试容器地址。Python 51 项、前端 70 项回归及前端生产构建通过；既有大包体积警告仍未解决，不据此声称性能验收通过。

最后一次 deployment profile 全 reactor clean package 通过，后端 64 项和生成器 1 项测试均成功。实际发布 Jar 未包含本机 Spring 配置、测试日志配置、完整应用测试类或 Testcontainers/Docker Java 测试依赖。Git 可达历史的秘密扫描命中数为 0；这项检查不替代正式代码审查或完整供应链安全验收。

清理回查曾发现后续 Tomcat 重建前一测试的空目录；已从当前 Tomcat 实现确认其直接写入进程级目录属性，并在测试中显式限定、恢复。修复后重新运行整个集成套件，临时目录、两类业务测试容器、最新 Ryuk 辅助容器及本机隔离测试数据库残留均为 0。测试专用控制台日志不再生成本机业务日志文件；最初试运行产生的两个测试日志文件已经按精确路径删除。

本次补齐实际 Redis 日活去重及故障降级验证，不包含提交后事件、幂等消费者、真实浏览器 HTTPS、邮件投递、完整目标拓扑或远端 CI 验收，第 0 阶段仍未全部完成。此前部署栈的待清理资源继续以[隔离栈记录](../runbooks/2026-08-31-isolated-stack-verification.md)为准，其中 6 个测试卷仍实际存在；未通过本轮操作绕过原清理拦截。

如果后续执行中断，先按上述标签、会话及名称核对容器状态；暂停中的 Redis 只能按精确 ID 恢复，本轮及上一轮的资源不得混合清理。

#### 19:41 复验中断待核对项

本机 Docker 短暂恢复后再次因 WSL bootstrap 退出码 13 而不可用。本轮用于检查宿主机环境变量隔离的复验尚未进入业务断言，不能计为业务失败或通过。19:48 线程栈确认测试 JVM 卡在 Docker 命名管道重连；决定终止该轮测试，待引擎稳定后先核对资源再重跑。

- 环境：本机 Docker 的独立 Testcontainers 测试，不涉及 Dify 或共享业务数据库。
- 已创建 MySQL 容器 ID：`8dc9a45a91f1d767eab37f65eccd1c1395bb2377b3ed4bc04fec175c2b8fa270`；Ryuk ID：`5d7afc65310c9ce1c104f83b92843c1fd2e340ffa839ec8e845a92cc3bb8a477`。Redis、MongoDB 尚未到达启动步骤。
- 临时目录：`C:\Users\31173\AppData\Local\Temp\lyw-identity-redis-test-952493869081608585`。
- 未清理原因与风险：引擎不可用，无法确认测试生命周期的容器清理结果；可能残留合成数据容器及空临时目录，不能报告无残留或复用为正式数据。
- 恢复后先检查上述 ID 与测试标签、名称及挂载类型，确认仅属本轮后清理；临时目录须按解析后的精确路径检查并删除，随后回查。不得与此前部署栈被拦截的清理批次混用。
- 清理结果：干净启动恢复引擎后，核对 MySQL 容器名称、批次 `00d96e56ae314e7ba7b91e64de170ffe`、测试标签、退出状态、tmpfs 及无宿主机挂载后精确删除；Ryuk 已不存在。会话 `6ac6303e-cd71-42b8-923a-2a0f4a387f51` 的容器残留为 0。上述空临时目录及此前遗漏的空目录 `lyw-identity-redis-test-3754671008319410724` 均经检查后删除，回查不存在。本轮清理已完成，不影响此前部署栈的待清理记录。
- 本机 Docker 恢复过程另记录于 `D:\DockerDesktopRecovery\20260831-restore-original-data\recovery-record.md`，该目录包含敏感数据盘副本，不得上传或提交仓库。

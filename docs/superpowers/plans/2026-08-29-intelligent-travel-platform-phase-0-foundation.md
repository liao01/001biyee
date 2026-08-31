# Intelligent Travel Platform Phase 0 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留现有用户与社区数据的前提下，建立邮箱认证、身份模块边界、版本化迁移、容器部署、持续集成和基础可观测性，为后续旅行行程模块提供稳定身份与运行基线。

**Architecture:** 后端仍是一个 Spring Boot 部署单元，但新增 `identity` 深模块，外部只调用 `IdentityApplicationService` 与 `CurrentMemberProvider`。新账户使用邮箱、BCrypt、短期访问令牌和可撤销刷新会话；旧双重 MD5 密码仅存在于兼容验证器，并在首次成功登录后收敛为 BCrypt。

**Tech Stack:** Java 17、Spring Boot 3.4.10、MyBatis、MySQL 8、Redis、Vue 3、Vitest、Python unittest、Docker Compose、Nginx、Prometheus。

**Spec:** GitHub Issue #12 — `https://github.com/liao01/001biyee/issues/12`

## Global Constraints

- 不使用短信验证码，不以手机号作为正式登录标识。
- 注册验证与密码重置统一使用具有短有效期的一次性邮件链接。
- MySQL 是正式业务数据的唯一事实源；Redis 数据必须允许失效或重建。
- 旧手机号与旧密码字段只能由显式兼容层读取，新注册和新密码写入不得继续产生旧格式。
- 所有迁移必须可重复执行，提供 dry-run 差异统计，并验证缺失项、多出项和旧规则残留。
- 真实秘密只通过 Git 忽略的本机配置或环境变量注入；示例值使用 `change-me`。
- 当前工作区的帖子分类改动已占用 `sql/travel_share.sql` 和迁移测试设施；执行前必须先完成该改动，或取得用户对独立 worktree 的明确授权。不得直接覆盖这些未提交改动。
- 每个提交步骤仅在用户已对该任务明确授权提交时执行；没有授权时保留已验证 diff 并报告，不提交。

---

## File Structure

### 身份模块

- `business/src/main/java/com/jiawa/lyw/identity/api/IdentityController.java`：邮箱注册、验证、登录、刷新、退出和密码重置 HTTP 接口。
- `business/src/main/java/com/jiawa/lyw/identity/api/IdentityRequests.java`：身份接口请求记录类型。
- `business/src/main/java/com/jiawa/lyw/identity/api/IdentityResponses.java`：身份接口响应记录类型。
- `business/src/main/java/com/jiawa/lyw/identity/application/IdentityApplicationService.java`：身份用例的唯一应用入口。
- `business/src/main/java/com/jiawa/lyw/identity/application/CurrentMemberProvider.java`：向其他业务模块提供当前会员 ID。
- `business/src/main/java/com/jiawa/lyw/identity/domain/MemberAccount.java`：账户状态与邮箱验证规则。
- `business/src/main/java/com/jiawa/lyw/identity/domain/IdentityException.java`：身份模块对外稳定异常类型，不暴露基础设施错误。
- `business/src/main/java/com/jiawa/lyw/identity/domain/PasswordHasher.java`：新密码哈希与旧密码升级接口。
- `business/src/main/java/com/jiawa/lyw/identity/domain/SessionTokens.java`：访问令牌与刷新令牌值对象。
- `business/src/main/java/com/jiawa/lyw/identity/infrastructure/IdentityMapper.java`：身份模块 MyBatis 持久化接口。
- `business/src/main/resources/mapper/identity/IdentityMapper.xml`：账户、一次性令牌和刷新会话 SQL。
- `business/src/main/java/com/jiawa/lyw/identity/infrastructure/BCryptPasswordHasher.java`：BCrypt 与旧双重 MD5 兼容验证。
- `business/src/main/java/com/jiawa/lyw/identity/infrastructure/JwtAccessTokenService.java`：15 分钟访问令牌。
- `business/src/main/java/com/jiawa/lyw/identity/infrastructure/RefreshSessionService.java`：随机刷新令牌、摘要存储、轮换和撤销。
- `business/src/main/java/com/jiawa/lyw/identity/infrastructure/OneTimeTokenService.java`：注册验证与密码重置的一次性令牌。
- `business/src/main/java/com/jiawa/lyw/identity/infrastructure/IdentityProperties.java`：身份有效期、公开地址与 Cookie 安全配置。
- `business/src/main/java/com/jiawa/lyw/identity/infrastructure/VerificationLinkMailer.java`：验证和重置邮件端口适配器。
- `business/src/main/java/com/jiawa/lyw/identity/application/DefaultIdentityApplicationService.java`：应用接口实现与事务边界。

### 迁移、前端与运行

- `sql/migrations/20260829_member_email_identity.sql`：邮箱账户、一次性令牌和刷新会话的收敛迁移。
- `tests/scripts/test_member_email_identity_migration.py`：迁移 dry-run、apply、重复执行与残留验证。
- `web/src/modules/identity/identityHttp.js`：用户端身份接口唯一调用适配器。
- `web/src/modules/identity/identitySession.js`：访问令牌内存状态与基于 HttpOnly Cookie 的刷新流程。
- `web/src/modules/identity/EmailVerificationResult.vue`：邮件验证结果页面。
- `web/src/modules/identity/PasswordReset.vue`：一次性链接密码重置页面。
- `compose.yaml`、`business/Dockerfile`、`web/Dockerfile`、`deploy/nginx.conf`：单服务器容器部署。
- `deploy/prometheus.yml`：Prometheus 抓取配置。
- `.github/workflows/ci.yml`：后端、前端、安全与迁移检查。

---

### Task 1: 建立可收敛的邮箱身份迁移

**执行状态：** 数据库纵切片已实现；#15 的应用身份链路继续在 Task 2–4 实施。

**正式事实源：**
- 空库结构：`sql/travel_share.sql`。
- 升级规则：`sql/migrations/20260829_member_email_identity.sql`。
- 执行、差异报告、兼容边界和恢复策略：`docs/data/identity-migration.md`。
- 测试参数注册：`tests/scripts/migration_specs.py`，复用 #13 的 `MigrationSpec`。
- 可执行行为契约：`tests/scripts/test_member_email_identity_migration.py`。

**验证：**
- [x] dry-run 不修改正式表结构和业务数据。
- [x] 邮箱规范化、冲突归属保护与无法自动迁移账户报告。
- [x] 重复执行、部分 DDL 恢复、已升级凭据及停用状态保护。
- [x] 空库结构与迁移结构一致；新账户不依赖历史字段。
- [x] 一次性令牌与刷新会话只保存摘要。

Run: `python -m unittest tests.scripts.test_member_email_identity_migration tests.scripts.test_post_category_migration tests.scripts.test_post_location_compatibility_migration -v`

仅在已获授权的目标环境执行迁移；本地隔离测试结果不代表已迁移生产数据。

---

### Task 2: 建立身份模块接口与架构边界

**执行状态：** 已实现并由架构测试验证。身份接口和领域类型位于 `business/src/main/java/com/jiawa/lyw/identity/`；领域类型不依赖 Spring 或 MyBatis。

**Files:**
- Modify: `business/pom.xml`
- Create: `business/src/main/java/com/jiawa/lyw/identity/application/IdentityApplicationService.java`
- Create: `business/src/main/java/com/jiawa/lyw/identity/application/CurrentMemberProvider.java`
- Create: `business/src/main/java/com/jiawa/lyw/identity/domain/MemberAccount.java`
- Create: `business/src/main/java/com/jiawa/lyw/identity/domain/IdentityException.java`
- Create: `business/src/main/java/com/jiawa/lyw/identity/domain/PasswordHasher.java`
- Create: `business/src/main/java/com/jiawa/lyw/identity/domain/SessionTokens.java`
- Create: `business/src/test/java/com/jiawa/lyw/architecture/IdentityModuleBoundaryTests.java`

**Interfaces:**
- Consumes: Task 1 的新身份 schema。
- Produces: `IdentityApplicationService` 用例入口与 `CurrentMemberProvider.memberId()` 当前用户接口。

- [x] **Step 1: 添加最小依赖并写边界失败测试**

在 `business/pom.xml` 添加 `spring-security-crypto`、`archunit-junit5`（test）、`spring-boot-starter-actuator` 和 `micrometer-registry-prometheus`。测试要求模块外不得直接访问身份基础设施：

```java
@AnalyzeClasses(packages = "com.jiawa.lyw")
class IdentityModuleBoundaryTests {
    @ArchTest
    static final ArchRule identityInfrastructureIsInternal = noClasses()
            .that().resideOutsideOfPackage("..identity..")
            .should().dependOnClassesThat().resideInAPackage("..identity.infrastructure..");
}
```

- [x] **Step 2: 运行边界测试确认红灯或缺类型**

Run from `business`: `.\mvnw.cmd -Dtest=IdentityModuleBoundaryTests test`

Expected: FAIL，直到依赖和身份包骨架存在。

- [x] **Step 3: 定义稳定接口和值对象**

```java
public interface CurrentMemberProvider {
    long memberId();
}

public final class IdentityException extends RuntimeException {
    public IdentityException(String message) { super(message); }
}

public record SessionTokens(
        String accessToken,
        Instant accessExpiresAt,
        String refreshToken,
        Instant refreshExpiresAt) {}

public interface PasswordHasher {
    String hash(String rawPassword);
    PasswordCheck verify(String rawPassword, String storedHash, String algorithm);
    record PasswordCheck(boolean matches, boolean needsUpgrade) {}
}
```

`MemberAccount` 仅表达 `id`、`email`、`emailVerifiedAt`、`passwordHash`、`passwordAlgorithm`、`name` 和账户状态，不包含 Controller 或 MyBatis 注解。

- [x] **Step 4: 定义应用服务签名**

```java
public interface IdentityApplicationService {
    void register(String email, String rawPassword);
    void verifyEmail(String rawToken);
    SessionTokens login(String email, String rawPassword);
    SessionTokens refresh(String rawRefreshToken);
    void logout(String rawRefreshToken);
    void requestPasswordReset(String email);
    void resetPassword(String rawToken, String newRawPassword);
}
```

- [x] **Step 5: 运行编译与架构测试**

Run from `business`: `.\mvnw.cmd -Dtest=IdentityModuleBoundaryTests test`

Expected: PASS。

- [x] **Step 6: 按授权提交**

```bash
git add business/pom.xml business/src/main/java/com/jiawa/lyw/identity business/src/test/java/com/jiawa/lyw/architecture/IdentityModuleBoundaryTests.java
git commit -m "refactor: establish identity module boundary"
```

---

### Task 3: 实现密码升级、访问令牌和刷新会话

**当前进度：** 已实现 BCrypt、登录事务内的首登升级和旧密码清除、访问令牌签发、刷新轮换与撤销。实际行为测试使用已确认的公开 HTTP + 真实 MySQL 接缝：`IdentityHttpIT`，不再采用下方计划示意中的内部服务测试类。执行命令为 `python -m scripts.run_backend_integration`；数据与配置链路见 `docs/data/identity-http.md`。访问令牌的请求验证和前端切换继续在 Task 5–6 实施。

**Files:**
- Create: `business/src/main/java/com/jiawa/lyw/identity/infrastructure/BCryptPasswordHasher.java`
- Create: `business/src/main/java/com/jiawa/lyw/identity/infrastructure/JwtAccessTokenService.java`
- Create: `business/src/main/java/com/jiawa/lyw/identity/infrastructure/RefreshSessionService.java`
- Create: `business/src/main/java/com/jiawa/lyw/identity/infrastructure/IdentityMapper.java`
- Create: `business/src/main/resources/mapper/identity/IdentityMapper.xml`
- Test: `business/src/test/java/com/jiawa/lyw/identity/infrastructure/BCryptPasswordHasherTests.java`
- Test: `business/src/test/java/com/jiawa/lyw/identity/infrastructure/RefreshSessionServiceTests.java`

**Interfaces:**
- Consumes: `PasswordHasher`、`SessionTokens` 和 Task 1 的 `identity_refresh_session`。
- Produces: BCrypt 新密码、旧双重 MD5 首登升级、15 分钟 access token、30 天单次轮换 refresh token。

- [ ] **Step 1: 写密码兼容测试**

```java
@Test
void legacyDoubleMd5MatchRequiresUpgrade() {
    var hasher = new BCryptPasswordHasher();
    var legacy = DigestUtil.md5Hex(DigestUtil.md5Hex("Secret123"));
    var result = hasher.verify("Secret123", legacy, "LEGACY_DOUBLE_MD5");
    assertTrue(result.matches());
    assertTrue(result.needsUpgrade());
}

@Test
void newHashesUseBcrypt() {
    var hasher = new BCryptPasswordHasher();
    var hash = hasher.hash("Secret123");
    assertTrue(hasher.verify("Secret123", hash, "BCRYPT").matches());
}
```

- [ ] **Step 2: 写刷新令牌轮换测试**

```java
@Test
void refreshRevokesOldTokenAndReturnsANewSession() {
    SessionTokens first = sessions.issue(42L);
    SessionTokens second = sessions.rotate(first.refreshToken());
    assertThrows(IdentityException.class,
            () -> sessions.rotate(first.refreshToken()));
    assertNotEquals(first.refreshToken(), second.refreshToken());
}
```

- [ ] **Step 3: 运行测试确认红灯**

Run from `business`: `.\mvnw.cmd -Dtest=BCryptPasswordHasherTests,RefreshSessionServiceTests test`

Expected: FAIL，因为实现不存在。

- [ ] **Step 4: 实现确定的令牌规则**

- BCrypt cost 固定为 12。
- access token 仅包含 `sub`、`iat`、`nbf`、`exp`、`jti`，有效期 15 分钟。
- refresh token 使用 `SecureRandom` 生成 32 字节并 Base64URL 编码，只将 SHA-256 摘要写入 MySQL，有效期 30 天。
- 每次 refresh 在同一事务中撤销旧会话并创建新会话。
- 旧密码匹配后立即把 BCrypt hash 与 `BCRYPT` 算法写回，不保留新的旧格式密码。

- [ ] **Step 5: 运行身份基础设施测试**

Run from `business`: `.\mvnw.cmd -Dtest=BCryptPasswordHasherTests,RefreshSessionServiceTests,RuntimeSecretConfigurationTests test`

Expected: PASS；日志中不出现原始密码、access token 或 refresh token。

- [ ] **Step 6: 按授权提交**

```bash
git add business/src/main/java/com/jiawa/lyw/identity/infrastructure business/src/main/resources/mapper/identity business/src/test/java/com/jiawa/lyw/identity/infrastructure
git commit -m "feat: add secure identity credentials and sessions"
```

---

### Task 4: 实现注册验证与密码重置邮件链路

**当前进度：** 新增链路已接入实际 MVC 拦截器及 MySQL 事务，使用 `IdentityHttpIT` 验证注册、一次性验证、密码重置和邮件失败回滚。邮件与时间在外部边界替换；未向真实用户投递邮件。实际运行命令与事实源见 `docs/data/identity-http.md`，下方代码及类名保留为原始设计示意。

**Files:**
- Create: `business/src/main/java/com/jiawa/lyw/identity/infrastructure/VerificationLinkMailer.java`
- Create: `business/src/main/java/com/jiawa/lyw/identity/infrastructure/OneTimeTokenService.java`
- Create: `business/src/main/java/com/jiawa/lyw/identity/infrastructure/IdentityProperties.java`
- Create: `business/src/main/java/com/jiawa/lyw/identity/application/DefaultIdentityApplicationService.java`
- Modify: `business/src/main/java/com/jiawa/lyw/Util/MailUtils.java`
- Create: `business/src/main/java/com/jiawa/lyw/Util/MailDeliveryException.java`
- Modify: `business/src/main/resources/application.properties.example`
- Test: `business/src/test/java/com/jiawa/lyw/identity/application/IdentityVerificationTests.java`

**Interfaces:**
- Consumes: `IdentityApplicationService`、`PasswordHasher`、Task 1 的一次性令牌表和邮件配置。
- Produces: 注册验证与密码重置的一次性链接；原始令牌只存在于发出的链接中。

- [ ] **Step 1: 写一次性令牌行为测试**

```java
@Test
void verificationTokenCanBeUsedOnlyOnce() {
    service.register("Alice@Example.com", "Secret123");
    String token = mailer.lastVerificationToken();
    service.verifyEmail(token);
    assertThrows(IdentityException.class, () -> service.verifyEmail(token));
    assertEquals("alice@example.com", accounts.requiredByEmail("alice@example.com").email());
}

@Test
void resetRequestDoesNotRevealWhetherEmailExists() {
    assertDoesNotThrow(() -> service.requestPasswordReset("missing@example.com"));
    assertEquals(0, mailer.sentCount());
}
```

- [ ] **Step 2: 运行测试确认红灯**

Run from `business`: `.\mvnw.cmd -Dtest=IdentityVerificationTests test`

Expected: FAIL，因为应用服务和令牌服务尚未实现。

- [ ] **Step 3: 实现一次性链接规则**

- 注册令牌有效期 24 小时，重置令牌有效期 30 分钟。
- 原始令牌为 32 字节安全随机值，数据库只保存 SHA-256 摘要。
- 校验时使用单条条件更新设置 `used_at`，条件包含摘要、用途、未使用和未过期，确保并发只能成功一次。
- 邮件链接分别为 `${APP_PUBLIC_URL}/verify-email?token=...` 与 `${APP_PUBLIC_URL}/reset-password?token=...`。
- `MailUtils` 不再捕获异常后 `printStackTrace()`；改为抛出不包含凭证的 `MailDeliveryException`。

- [ ] **Step 4: 添加明确配置**

```java
public record IdentityProperties(
        URI publicUrl,
        Duration verificationTtl,
        Duration passwordResetTtl,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean secureCookie) {
    public IdentityProperties {
        if (!Set.of("http", "https").contains(publicUrl.getScheme())) {
            throw new IllegalArgumentException("app.public-url must use http or https");
        }
    }
}
```

```properties
app.public-url=${APP_PUBLIC_URL:http://localhost:5173}
identity.verification-ttl=24h
identity.password-reset-ttl=30m
identity.access-token-ttl=15m
identity.refresh-token-ttl=30d
identity.secure-cookie=${IDENTITY_COOKIE_SECURE:true}
```

- [ ] **Step 5: 运行验证链路测试**

Run from `business`: `.\mvnw.cmd -Dtest=IdentityVerificationTests,RuntimeSecretConfigurationTests test`

Expected: PASS；不存在邮箱的重置请求返回与存在邮箱相同的公开结果。

- [ ] **Step 6: 按授权提交**

```bash
git add business/src/main/java/com/jiawa/lyw/identity business/src/main/java/com/jiawa/lyw/Util/MailUtils.java business/src/main/java/com/jiawa/lyw/Util/MailDeliveryException.java business/src/main/resources/application.properties.example business/src/test/java/com/jiawa/lyw/identity/application
git commit -m "feat: add one-time email identity links"
```

---

### Task 5: 暴露身份 HTTP 契约并关闭短信入口

**Files:**
- Create: `business/src/main/java/com/jiawa/lyw/identity/api/IdentityRequests.java`
- Create: `business/src/main/java/com/jiawa/lyw/identity/api/IdentityResponses.java`
- Create: `business/src/main/java/com/jiawa/lyw/identity/api/IdentityController.java`
- Modify: `business/src/main/java/com/jiawa/lyw/config/SpringMvcConfig.java`
- Modify: `business/src/main/java/com/jiawa/lyw/interceptor/WebLoginInterceptor.java`
- Modify: `business/src/main/java/com/jiawa/lyw/exception/BusinessExceptionEnum.java`
- Modify: `business/src/main/java/com/jiawa/lyw/controller/web/MemberController.java`
- Modify: `business/src/main/java/com/jiawa/lyw/controller/web/SmsCodeController.java`
- Test: `business/src/test/java/com/jiawa/lyw/identity/api/IdentityControllerTests.java`

**Interfaces:**
- Consumes: 完整 `IdentityApplicationService`。
- Produces: `/web/identity/register`、`/verify-email`、`/login`、`/refresh`、`/logout`、`/request-password-reset`、`/reset-password`。

- [ ] **Step 1: 写 HTTP 契约失败测试**

```java
mockMvc.perform(post("/web/identity/login")
        .contentType(APPLICATION_JSON)
        .content("{\"email\":\"alice@example.com\",\"password\":\"Secret123\"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.content.accessToken").isString())
    .andExpect(jsonPath("$.content.refreshToken").doesNotExist())
    .andExpect(cookie().httpOnly("refresh_token", true))
    .andExpect(cookie().secure("refresh_token", true));

mockMvc.perform(post("/web/sms-code/send-for-register"))
    .andExpect(status().isNotFound());
```

- [ ] **Step 2: 运行 Controller 测试确认红灯**

Run from `business`: `.\mvnw.cmd -Dtest=IdentityControllerTests test`

Expected: FAIL，因为新路由不存在且短信路由仍公开。

- [ ] **Step 3: 实现请求与响应类型**

```java
public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
public record TokenResponse(String accessToken, Instant accessExpiresAt) {}
```

请求字段以 `IdentityController` 的记录类型为正式契约；新密码规则以 `PasswordPolicy` 为正式事实源，长度按其 Unicode 字符及 UTF-8 字节规则校验。登录与刷新在响应中写入 `HttpOnly; Secure; SameSite=Lax; Path=/` 的 refresh Cookie，响应体只返回 access token；退出同时撤销服务端会话并清除 Cookie。生产环境 `Secure` 固定为 true，本地纯 HTTP 开发可通过 `IDENTITY_COOKIE_SECURE=false` 显式覆盖。

- [ ] **Step 4: 切换拦截器到标准 Authorization 头**

`WebLoginInterceptor` 只读取 `Authorization: Bearer <access-token>`，验证后把 JWT `sub` 转为会员 ID。公开路由只包含注册、验证、登录、刷新、重置申请、重置确认和公开内容读取。`MemberController` 删除旧注册、登录和短信重置入口，只保留仍被使用的心跳接口；`SmsCodeController` 移除 Controller 注册，使短信发送路径返回 404。服务类暂时保留供迁移审计，任何新调用都由防回流测试阻止。

- [ ] **Step 5: 运行身份接口与原有匿名内容测试**

Run from `business`: `.\mvnw.cmd -Dtest=IdentityControllerTests,PostDetailAnonymousAccessTests test`

Expected: PASS；短信发送路径返回 404，公开帖子详情仍可匿名读取。

- [ ] **Step 6: 按授权提交**

```bash
git add business/src/main/java/com/jiawa/lyw/identity/api business/src/main/java/com/jiawa/lyw/config/SpringMvcConfig.java business/src/main/java/com/jiawa/lyw/interceptor/WebLoginInterceptor.java business/src/main/java/com/jiawa/lyw/exception/BusinessExceptionEnum.java business/src/main/java/com/jiawa/lyw/controller/web/MemberController.java business/src/main/java/com/jiawa/lyw/controller/web/SmsCodeController.java business/src/test/java/com/jiawa/lyw/identity/api
git commit -m "feat: expose email identity api"
```

---

### Task 6: 将用户端切换到邮箱身份流程

**Files:**
- Create: `web/src/modules/identity/identityHttp.js`
- Create: `web/src/modules/identity/identityHttp.test.js`
- Create: `web/src/modules/identity/identitySession.js`
- Create: `web/src/modules/identity/identitySession.test.js`
- Create: `web/src/modules/identity/EmailVerificationResult.vue`
- Create: `web/src/modules/identity/PasswordReset.vue`
- Modify: `web/src/view/register.vue`
- Modify: `web/src/view/login.vue`
- Modify: `web/src/view/forgot.vue`
- Modify: `web/src/router/index.js`
- Modify: `web/src/store/index.js`
- Modify: `web/src/utils/request.js`

**Interfaces:**
- Consumes: Task 5 身份 HTTP 契约。
- Produces: 不包含短信或客户端 MD5 的邮箱注册、登录、验证和重置体验。

- [ ] **Step 1: 写 HTTP 适配器失败测试**

```javascript
it('sends raw password only to the email identity endpoint', async () => {
  mock.onPost('/web/identity/login').reply(200, { success: true, content: tokens })
  await identityHttp.login({ email: 'alice@example.com', password: 'change-me' })
  expect(JSON.parse(mock.history.post[0].data)).toEqual({
    email: 'alice@example.com', password: 'change-me'
  })
})
```

测试不得断言或记录真实密码；示例值仅用于单元测试。

- [ ] **Step 2: 写单次自动刷新测试**

```javascript
it('refreshes once after a 401 and retries the original request', async () => {
  api.onGet('/web/member/heart').replyOnce(401).onGet('/web/member/heart').reply(200)
  auth.onPost('/web/identity/refresh').reply(200, { success: true, content: rotated })
  await request.get('/web/member/heart')
  expect(auth.history.post).toHaveLength(1)
  expect(api.history.get).toHaveLength(2)
})
```

- [ ] **Step 3: 运行前端测试确认红灯**

Run from `web`: `npm test -- src/modules/identity/identityHttp.test.js src/modules/identity/identitySession.test.js`

Expected: FAIL，因为模块尚不存在。

- [ ] **Step 4: 实现唯一身份调用适配器与会话规则**

```javascript
export const identityHttp = {
  register: (body) => request.post('/web/identity/register', body),
  verifyEmail: (token) => request.post('/web/identity/verify-email', { token }),
  login: (body) => request.post('/web/identity/login', body),
  refresh: () => request.post('/web/identity/refresh', {}, { withCredentials: true }),
  logout: () => request.post('/web/identity/logout', {}, { withCredentials: true }),
  requestPasswordReset: (email) => request.post('/web/identity/request-password-reset', { email }),
  resetPassword: (token, newPassword) => request.post('/web/identity/reset-password', { token, newPassword })
}
```

access token 只保存在内存状态；前端 JavaScript 不读取 refresh token。应用启动时通过携带 HttpOnly Cookie 的 refresh 请求恢复会话。请求拦截器写入 `Authorization` 头；并发 401 共享一个 refresh Promise，刷新失败统一清空会话并打开登录入口。

将 `web/src/utils/request.js` 的 axios 实例设置为 `withCredentials: true`，确保登录响应可以写入 Cookie，刷新和退出请求可以携带 Cookie。

- [ ] **Step 5: 重写三个表单并添加两个路由**

- 注册页只收集邮箱、密码和确认密码，成功后提示检查邮箱。
- 登录页只收集邮箱与密码，不做客户端 MD5，不展示短信或图片验证码。
- 忘记密码页只提交邮箱并始终显示同一成功提示。
- `/verify-email` 读取 query token 并展示成功、已失效或失败状态。
- `/reset-password` 读取 query token，提交两次一致的新密码。

- [ ] **Step 6: 运行身份与应用外壳测试**

Run from `web`: `npm test -- src/modules/identity src/components/AppShell.test.js`

Expected: PASS。

Run from `web`: `npm run build`

Expected: PASS，构建产物中搜索不到 `/sms-code/`、`hexMd5Key(` 或“短信验证码”。

- [ ] **Step 7: 按授权提交**

```bash
git add web/src/modules/identity web/src/view/register.vue web/src/view/login.vue web/src/view/forgot.vue web/src/router/index.js web/src/store/index.js web/src/utils/request.js
git commit -m "feat: switch web app to email identity"
```

---

### Task 7: 建立单服务器容器部署与基础可观测性

**Files:**
- Create: `compose.yaml`
- Create: `.env.example`
- Create: `business/Dockerfile`
- Create: `web/Dockerfile`
- Create: `deploy/nginx.conf`
- Create: `deploy/prometheus.yml`
- Modify: `business/src/main/resources/application.properties.example`
- Modify: `.gitignore`
- Test: `business/src/test/java/com/jiawa/lyw/RuntimeSecretConfigurationTests.java`

**Interfaces:**
- Consumes: 可构建的 business 与 web、环境变量配置。
- Produces: Nginx、business、MySQL、Redis、MongoDB、MinIO、OpenSearch 和 Prometheus 的单机部署拓扑。

- [ ] **Step 1: 扩展运行配置失败测试**

```java
@Test
void publicUrlMustUseHttpOrHttps() {
    assertThrows(IllegalArgumentException.class,
            () -> new IdentityProperties(URI.create("javascript:alert(1)"), Duration.ofHours(24),
                    Duration.ofMinutes(30), Duration.ofMinutes(15), Duration.ofDays(30), true));
}
```

同时用 `rg` 断言 `compose.yaml` 和 `.env.example` 不含疑似真实密钥格式。

- [ ] **Step 2: 创建最小多阶段镜像**

`business/Dockerfile` 使用 Maven 构建阶段与 JRE 17 运行阶段；`web/Dockerfile` 使用 Node 22 构建并由 Nginx 托管。运行容器使用非 root 用户，镜像内不复制本机 `application.properties` 或 `.env`。

- [ ] **Step 3: 定义 Compose 健康与持久化规则**

- `business` 依赖 MySQL、Redis 健康检查，通过环境变量接收连接与秘密。
- MySQL、Redis、MongoDB、MinIO、OpenSearch 使用命名卷。
- Nginx 只暴露 80/443，数据组件不对公网发布端口。
- OpenSearch 设置单节点和明确内存上限；Prometheus 只抓取 `/actuator/prometheus`。
- 所有服务设置 `restart: unless-stopped` 和资源上限。

- [ ] **Step 4: 暴露有限 Actuator 端点**

```properties
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.probes.enabled=true
management.metrics.tags.application=${spring.application.name}
```

`health` 仅返回组件状态，不返回数据库 URL、用户名、邮件账户或外部 API 密钥。

- [ ] **Step 5: 验证 Compose 与镜像构建**

Run: `docker compose --env-file .env.example config`

Expected: PASS，所有变量解析且无无效 YAML。

Run: `docker compose --env-file .env.example build business web`

Expected: PASS。

Run: `docker compose --env-file .env.example up -d mysql redis mongodb minio opensearch prometheus business web`

Expected: `docker compose ps` 中所有带健康检查的服务为 healthy；`/business/actuator/health` 返回 `UP`。

- [ ] **Step 6: 精确清理测试环境**

Run: `docker compose --env-file .env.example down`

Expected: 容器和网络停止；不使用 `--volumes`，保留持久卷，除非用户另行明确授权删除测试数据。

- [ ] **Step 7: 按授权提交**

```bash
git add compose.yaml .env.example business/Dockerfile web/Dockerfile deploy .gitignore business/src/main/resources/application.properties.example business/src/test/java/com/jiawa/lyw/RuntimeSecretConfigurationTests.java
git commit -m "chore: add observable single-server deployment"
```

---

### Task 8: 建立持续集成与第 0 阶段验收门禁

**执行记录（2026-08-31）：** CI 定义已落地；README 已切换为实际邮箱身份与当前配置入口。[备份恢复手册](../../runbooks/backup-and-restore.md)和[身份事件响应手册](../../runbooks/identity-incident-response.md)已补齐当前拓扑的操作步骤和验证记录，具体能力与未完成范围以这两份手册为准。当前物理备份覆盖上传目录，目标对象存储落地后仍须补齐其恢复方案；未将这一差异标记为完整交付。本文下方最初计划中的示意路径和流程须以当前部署配置及手册为执行依据，不能对现有环境盲目套用。

**追加验证（2026-08-31）：** 独立 MySQL 容器已完成 CI 同镜像的迁移与真实身份联调，资源已精确清理，证据见[迁移复验记录](../../data/identity-migration.md#2026-08-31-docker-恢复后的隔离复验)。该记录不代替远端 CI 或完整部署栈验收。

**仍未通过的门禁：** 远端 CI、完整部署栈复验、加密异地备份及恢复演练、真实浏览器与生产 HTTPS/邮件验收。不会以本地单元测试、文档存在或脚本语法检查代替运行证据；本 Task 和第 0 阶段仍未全部完成。

**Files:**
- Create: `.github/workflows/ci.yml`
- Modify: `README.md`
- Modify: `SECURITY.md`
- Create: `docs/runbooks/backup-and-restore.md`
- Create: `docs/runbooks/identity-incident-response.md`

**Interfaces:**
- Consumes: Tasks 1–7 的测试和构建命令。
- Produces: 可重复的 CI 门禁、部署说明、备份恢复与身份事件处置步骤。

- [ ] **Step 1: 定义 CI 作业**

CI 使用最小权限 `contents: read`，包含四个并行作业：

```yaml
permissions:
  contents: read
jobs:
  backend:
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17', cache: maven }
      - run: ./mvnw -B test
        working-directory: business
  frontend:
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '22', cache: npm, cache-dependency-path: web/package-lock.json }
      - run: npm ci
        working-directory: web
      - run: npm test
        working-directory: web
      - run: npm run build
        working-directory: web
```

另两个作业运行 `python -m unittest tests.scripts.security` 与 Docker Compose 配置检查；MySQL 迁移作业使用临时 MySQL 8 service，测试结束由 CI runner 销毁。

- [ ] **Step 2: 更新运行与恢复文档**

README 明确 JDK 17、Node 22、Docker Compose、环境变量、邮箱链接流程和无短信依赖。备份文档给出 MySQL、MongoDB、MinIO 卷的备份命令、恢复顺序、RPO 24 小时和恢复演练记录格式。身份事件文档给出撤销刷新会话、轮换 JWT secret、强制重新登录和检查审计记录的顺序。

- [ ] **Step 3: 运行完整本地门禁**

Run from root:

```powershell
python -m unittest discover -s tests -v
Set-Location business
.\mvnw.cmd test
Set-Location ..\web
npm test
npm run build
Set-Location ..
docker compose --env-file .env.example config
```

Expected: 所有命令 exit 0；测试输出不包含真实秘密或原始令牌。

- [ ] **Step 4: 运行防回流搜索**

Run:

```powershell
rg -n "sms-code|SmsCodeService|手机号.*登录|hexMd5Key\(" web business/src/main/java/com/jiawa/lyw/identity
```

Expected: 新身份模块和用户端没有匹配；旧短信类若仍保留，只能位于明确兼容区域且不再注册 HTTP 入口。

- [ ] **Step 5: 审查意图内差异与供应链**

Run: `git diff --check`

Run: `git diff -- business/pom.xml web/package-lock.json compose.yaml .github/workflows/ci.yml`

Expected: 依赖只包含计划列出的库；Actions 固定到官方主版本；Compose 不挂载 Docker socket、不使用 privileged、不含明文秘密。

- [ ] **Step 6: 按授权提交第 0 阶段文档与门禁**

```bash
git add .github/workflows/ci.yml README.md SECURITY.md docs/runbooks
git commit -m "docs: add phase zero operations and ci gates"
```

- [ ] **Step 7: 第 0 阶段完成检查**

只有以下证据齐全才进入第 1 阶段计划：

- 邮箱注册、验证、登录、刷新、退出和重置端到端通过。
- 旧邮箱形态账号首登后密码收敛到 BCrypt；无法自动迁移的手机号账号有明确数量报告。
- 短信 HTTP 入口不可访问，新前端无短信和客户端 MD5 路径。
- 迁移 dry-run、apply 和重复执行测试通过。
- Docker Compose 健康检查、Prometheus 指标和备份恢复演练通过。
- CI 所有 required jobs 通过，且无已知秘密或高危安全阻塞。

---

## Successor Plan Boundaries

本计划只实现已确认规格的第 0 阶段。其余需求不是本计划遗漏，而是按可独立验收的软件边界拆分：

1. 第 1 阶段计划：旅行行程领域、状态生命周期、结构化 AI 建议、校验、差异确认和行程编辑器。输入接口以本计划最终落地的 `CurrentMemberProvider.memberId()` 和部署基线为准。
2. 第 2 阶段计划：地点适配器、每日地图、路线规划、酒店与交通参考、缓存降级和手工录入。输入接口以第 1 阶段确定的行程安排项契约为准。
3. 第 3 阶段计划：负责人和成员权限、邀请通知、分类预算、共同费用分摊、打卡和执行记录。输入接口以第 1 阶段的行程聚合与第 2 阶段的地点引用为准。
4. 第 4 阶段计划：公开行程、独立复制、AI 游记、混合推荐、自动风险分流、人工复核、举报和申诉。输入接口以前三个阶段的正式事实源为准。
5. 第 5 阶段计划：性能、安全、备份恢复、AI 成本看板、稳定演示数据和作品集证据。它只消费前四个阶段已经验证的运行指标与业务事件。

每个后继计划在其前置阶段验收通过后编写，避免提前固化尚未产生的类型和接口。任何后继计划都必须重新引用 GitHub Issue #12，并继承本计划 Global Constraints。

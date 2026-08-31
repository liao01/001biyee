# 邮箱身份 HTTP 与数据链路

本记录覆盖 #15 的新增邮箱链路与 #16 的后端切换。#16 的前端邮箱表单和会话恢复尚未完成；在完成切换前，不得把新接口测试通过当作可发布验收。

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

### 环境要求

生产 `APP_PUBLIC_URL` 必须显式配置为实际用户访问的前端基础地址，包含部署子路径（如果存在），不得包含用户名、密码、查询或片段。邮件链接保留该子路径。配置值通过环境变量或 Git 忽略的运行文件注入，不依赖请求 Host 生成链接。

生产刷新 Cookie 强制 `Secure`，需要 HTTPS 入口。本地纯 HTTP 开发可显式设置 `IDENTITY_COOKIE_SECURE=false`，不能据此降低生产配置。SMTP 使用加密连接并验证服务器主机名；邮件凭据仍使用既有环境配置。

本记录不声称已经配置生产 HTTPS、投递真实邮件、迁移共享数据库或部署应用。这些操作需要对应环境授权与验证。

## 本地与 CI 验证

从仓库根运行：

```powershell
python -m scripts.run_backend_integration
```

运行器优先使用 `LYW_MIGRATION_TEST_DSN`，否则读取既有 Git 忽略的本地 MySQL 配置，拒绝非回环目标。凭据只通过子进程环境传递，不放在命令参数或输出中。

每次测试创建带随机批次标识的 `lyw_identity_http_test_` 隔离库，使用正式空库结构；不修改原业务库。测试通过真实 MVC、拦截器、MyBatis 和 MySQL 事务执行，邮件和时间采用明确的外部边界替身，不发送真实邮件。测试结束精确删除本次数据库并回查不存在。

普通 Maven `test` 不要求 MySQL，也不自动运行 `*IT`。CI 的 MySQL 作业另行执行上述运行器，不能用普通单元测试成功代替集成测试证据。远端 CI 及同镜像容器复验状态见迁移记录。

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

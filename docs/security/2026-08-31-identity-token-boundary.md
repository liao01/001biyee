# 会员与管理员令牌边界处置记录

## 本地证据与影响

2026-08-31 实施 #16 时发现，旧管理端拦截器只检查 JWT 签名和时间，不检查解析后是否存在管理员身份。新会员令牌与旧管理员令牌使用同一配置的签名密钥；会员凭据因而可以通过管理端拦截器，即使解析出的管理员 ID 为空。受拦截器保护但未再次检查管理员身份的处理器可能被会员调用，属于高风险鉴权边界缺陷。

最小验证仅使用隔离 MySQL 测试库、合成签名密钥和测试专用只读处理器，没有调用实际管理业务、扫描外部环境或读取真实管理员凭据。`IdentityHttpIT.memberAccessTokenCannotAuthenticateAsAnAdministrator` 使用真实会员登录响应；修复前预期 401、实际 200，修复后返回 401。测试使用实际当前时间，避免旧管理员验证器因固定测试时间过期而产生假阳性。

## 本地修复与事实源

- [AdminLoginInterceptor.java](../../business/src/main/java/com/jiawa/lyw/interceptor/AdminLoginInterceptor.java) 显式拒绝会员凭据，并要求既有管理员身份字段有效；每次请求开始清除线程残留身份。
- [JwtAccessTokenService.java](../../business/src/main/java/com/jiawa/lyw/identity/infrastructure/JwtAccessTokenService.java) 是新会员访问令牌的正式签发及验证规则。会员验证使用固定签名算法、完整声明约束、有效期上限和正式账户状态，不接受管理员凭据。
- [IdentityHttpIT.java](../../business/src/test/java/com/jiawa/lyw/identity/api/IdentityHttpIT.java) 从真实 HTTP 链路约束身份隔离；[SensitiveLoggingTests.java](../../business/src/test/java/com/jiawa/lyw/security/SensitiveLoggingTests.java) 覆盖合法管理员凭据仍可用、历史会员声明及不完整管理员声明被拒绝、线程身份清除和日志不包含令牌。

## 门禁与未完成事项

本地最小复现和修复不等于完整管理端安全审计，不证明旧路由排除项、所有管理业务和线上版本安全。前后端身份迁移尚未整体完成，禁止将此阶段结果当作合并或发布许可。

已经询问是否授权管理端鉴权深度安全扫描，尚未获得确认，未启动扩展扫描。未推送、部署、访问生产数据、轮换签名密钥或撤销线上会话。若线上已部署同类混用逻辑，需由系统负责人确认版本及受影响范围，在取得环境授权后安排更新和会话处置。

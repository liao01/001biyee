# 身份事件响应运行手册

适用于邮箱身份链路的凭据泄露、账户异常和备份恢复。本文不授权生产 SQL、账号停用、密钥轮换、服务重建或用户通知；执行前确认事件编号、环境、影响范围和处置权限。

## 事实源与限制

- HTTP、Cookie、退出及密码重置语义见[身份链路](../data/identity-http.md)；持久化动作以 [IdentityMapper.xml](../../business/src/main/resources/mapper/identity/IdentityMapper.xml) 为准。
- 访问凭据验签规则由 [JwtAccessTokenService](../../business/src/main/java/com/jiawa/lyw/identity/infrastructure/JwtAccessTokenService.java) 定义；运行变量名由 [.env.example](../../.env.example) 维护。不要另外配置一套令牌时长或账号状态枚举。
- 撤销刷新会话不会立即撤销已签发的访问凭据；密码重置也不能代替立即访问封禁。仅清浏览器缓存、删除 Cookie 或重启后端均不足以终止所有会话。
- 会员和管理员身份不是同一权限边界。当前管理员仍使用兼容鉴权；共同使用的签名秘密轮换会影响管理员登录，但“撤销会员刷新会话”不等于撤销管理员凭据。涉及管理员时单独核对其入口与凭据处置。
- 本地修复与历史运行日志处置是两件事，相关证据见[日志记录](../security/2026-08-31-controller-logging.md)、[令牌边界记录](../security/2026-08-31-identity-token-boundary.md)和[跨域记录](../security/2026-08-31-identity-cors.md)。

## 处置顺序

1. **确定范围并保全证据。** 记录首次异常时间、发布版本、受影响凭据类别、已核实的账户范围及数量。只保留脱敏信息，不输出密码、邮箱链接、访问/刷新原文、全量 Cookie 或运行环境。真实取证材料放受保护位置；不要先删日志。
2. **阻止新增影响。** 经授权暂停对应写入口及全部身份写入者，包括后端实例与运维脚本。仅关闭前端页面不阻止 HTTP 调用。若是外部供应商密钥泄露，应在其供应商处吊销；本文不代表已执行吊销。
3. **撤销数据库凭据。** 在维护窗口内通过受保护的数据库连接，按下节执行指定账户或经单独批准的全局范围。撤销刷新会话和未使用的一次性链接必须在同一事务完成。应用的并发刷新或链接签发尚未停止时不能宣称已完成撤销。
4. **处理访问凭据。** 若仅影响一个账户，可通过正式账户停用能力阻止后续会员身份读取；当前阶段还没有完成该运营界面，不假装已有可调用的停用 API。需要立即让所有旧访问凭据失效时，另行授权轮换签名秘密，并接受所有会员和管理员重新登录的影响。若不轮换或停用，必须明确记录旧访问凭据在既定有效期内仍可能使用。
5. **必要时重置密码。** 安全恢复邮箱控制权后再走正式重置流程；不直接手填数据库密码哈希、不恢复历史密码字段、不重新启用短信。仅撤销会话不解决密码已经泄露的问题。
6. **验证再恢复入口。** 新登录成功、旧凭据被拒绝、撤销数量回查通过且没有新的异常签发后，才经授权恢复入口。报告通知范围、未解决风险和后续动作；不能用“部署了修复”代替历史数据处置。

## 受限数据库维护

维护前保存可恢复的受保护备份，确认当前数据库、停写状态与目标数量。通过已有安全连接输入 SQL，不把密码放在命令行或 Shell 历史中。以下针对单个账户的脚本默认 `ROLLBACK`，只预演数量。操作者必须在同一数据库会话中先把 `@incident_member_id` 设置为已核实的正整数；未设置时范围为空，不能把 0 行误报为完成。用户 ID 不从可疑请求直接复制。

```sql
START TRANSACTION;
SET @incident_now = UTC_TIMESTAMP(6);
SELECT COUNT(*) AS target_members
FROM member WHERE id = @incident_member_id;
SELECT COUNT(*) AS refresh_rows_to_revoke
FROM identity_refresh_session
WHERE member_id = @incident_member_id AND revoked_at IS NULL;
SELECT COUNT(*) AS link_rows_to_invalidate
FROM identity_one_time_token
WHERE member_id = @incident_member_id AND used_at IS NULL;
UPDATE identity_refresh_session SET revoked_at = @incident_now
WHERE member_id = @incident_member_id AND revoked_at IS NULL;
SELECT ROW_COUNT() AS revoked_refresh_rows;
UPDATE identity_one_time_token SET used_at = @incident_now
WHERE member_id = @incident_member_id AND used_at IS NULL;
SELECT ROW_COUNT() AS invalidated_link_rows;
SELECT COUNT(*) AS remaining_refresh_rows
FROM identity_refresh_session
WHERE member_id = @incident_member_id AND revoked_at IS NULL;
SELECT COUNT(*) AS remaining_link_rows
FROM identity_one_time_token
WHERE member_id = @incident_member_id AND used_at IS NULL;
ROLLBACK;
```

只有目标会员数量等于 1、变更数量符合预期且回查均为 0，才允许经确认将最后一条替换为 `COMMIT` 并重新执行。提交后另开事务回查该范围；重复执行应更新 0 行。字段被设为失效不代表用户确实点击过链接，必须在事件记录注明这是维护失效操作。

### 全局失效：仅用于单独批准的范围

签名秘密泄露或从旧数据库备份恢复时，若只换密钥，旧刷新会话仍可换取新访问凭据；旧邮箱链接也可能继续生效。下列事务涵盖全部会员，不能用来替代单账户处置。应用必须仍处于停写状态，同样默认回滚。

```sql
START TRANSACTION;
SET @incident_now = UTC_TIMESTAMP(6);
SELECT COUNT(*) AS refresh_rows_to_revoke
FROM identity_refresh_session WHERE revoked_at IS NULL;
SELECT COUNT(*) AS link_rows_to_invalidate
FROM identity_one_time_token WHERE used_at IS NULL;
UPDATE identity_refresh_session SET revoked_at = @incident_now
WHERE revoked_at IS NULL;
SELECT ROW_COUNT() AS revoked_refresh_rows;
UPDATE identity_one_time_token SET used_at = @incident_now
WHERE used_at IS NULL;
SELECT ROW_COUNT() AS invalidated_link_rows;
SELECT COUNT(*) AS remaining_refresh_rows
FROM identity_refresh_session WHERE revoked_at IS NULL;
SELECT COUNT(*) AS remaining_link_rows
FROM identity_one_time_token WHERE used_at IS NULL;
ROLLBACK;
```

先审核范围与预计数量，再经授权用 `COMMIT` 执行并独立回查。不删除账户、密码或历史凭据记录，不恢复旧值来“撤回”事件处置。合法用户需要重新登录或重新申请邮件链接。

## 签名秘密轮换与强制重新登录

1. 在秘密管理系统或权限受限的本机配置中生成满足当前服务校验要求的独立随机签名秘密。不得在终端输出值或把它写进普通备份、提交、Issue、CI 日志。
2. 保持所有身份写入者停止，完成已授权范围的数据库撤销后，把新秘密注入所有后端实例。当前 Compose 通过环境文件注入：仅修改文件再 `restart` 不会可靠更新容器创建时的环境，须用已经核对的发布配置重建后端。
3. 重建时沿用已审查的应用镜像和数据挂载，不顺便升级数据库或改写 Dify 网络。使用 `docker compose -f /opt/lyw/compose.yaml up -d --no-deps --force-recreate lyw-backend` 前，必须已确认数据库健康、发布配置及授权；命令只适用于当前默认生产布局，不是隔离演练配置。
4. 不输出 `docker inspect` 的环境数组，也不运行会展开秘密的完整 `docker compose config`；仅使用限定状态字段或 `config --quiet` 检查。
5. 用受保护的验证会话核对旧访问凭据被拒绝、旧刷新 Cookie 无法刷新、旧一次性链接不能再用，新登录及管理员重新登录按各自边界正常。真实账户操作须经授权；在本地测试用合成账户，不发送真实邮件。
6. 密钥轮换失败时保持入口关闭并调查，不能把泄露的旧密钥恢复上线作为常规回滚。恢复后的页面会清空内存身份并要求重新登录；以 API 拒绝旧凭据为证据，而不是仅看 UI 跳转。

## 验证与记录

本地运行 `python -m scripts.run_backend_integration` 可验证现有一次性链接、刷新撤销、密码重置及会员/管理员边界；它不证明某个生产事件已经处置，也不执行上述运维 SQL 或实际秘密轮换。

事件记录至少包含：事件编号、授权环境与范围、起止时间、采取的措施、目标/更新/残留数量、复验结论、用户影响、受保护证据位置、清理状态、未完成项。使用 `docs/security/` 中日期命名的脱敏记录，不把秘密或受影响人的身份写进仓库。

2026-08-31：手册基于当前 Mapper、访问凭据服务及 Compose 整理。从本文提取两段 SQL，在正式空库结构初始化的随机批次本地 MySQL 隔离库执行，验证：未设置目标及两种预演均回滚；单账户提交保留其他账户和历史失效时间；重复提交更新零行；全局提交收敛且不删除账户。测试使用明确标注的合成数据，隔离库已精确删除并回查不存在。

同轮 5 项部署契约测试、4 段 Bash 语法检查、文档文件链接检查通过；工作树秘密扫描发现数为 0。这些证据不替代容器恢复或线上密钥轮换演练。尚未对生产执行任何撤销或轮换；生产事件响应演练、管理员全链路复验与历史日志处置仍需单独授权和证据。

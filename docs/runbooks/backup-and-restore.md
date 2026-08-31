# 备份与恢复运行手册

适用于当前已实现的部署拓扑，不代表已执行生产备份或恢复演练。执行停止服务、读取生产数据、上传异地副本、恢复或切换之前，必须取得对应环境和范围的授权。本文命令面向已授权 Linux 主机的 Bash，不能原样粘贴到 PowerShell。

## 正式来源与覆盖范围

- 服务名称、镜像和挂载关系以 [Compose](../../deploy/compose.yaml) 为准；主机布局及 Dify 共存边界以[部署设计](../superpowers/specs/2026-08-30-tencent-lighthouse-constrained-deployment-design.md)为准。
- MySQL 保存正式业务数据；MongoDB 保存聊天原文；当前图片仍位于上传目录。一次冷备必须在相同停写窗口覆盖三者，不能只恢复数据库而丢失图片。
- Redis 是可重建数据，不从旧备份恢复会话或缓存。Prometheus 历史不是业务恢复的前置条件；需要保留时另行批准归档范围。
- 当前 Compose 没有 MinIO 或 OpenSearch。下述上传目录备份不能冒充 MinIO 备份；对象存储落地时必须补充桶、对象版本、元数据、凭据恢复和校验演练，搜索则从正式事实源重建。
- 运行秘密不进入普通备份包。数据库物理文件本身仍含密码摘要、令牌摘要和用户数据，必须加密。解密私钥与备份分开保管，恢复所需运行秘密通过受保护的秘密管理流程另行提供。

## 恢复目标与调度

第 0 阶段目标 RPO 为 24 小时：最新可恢复的异地加密副本不能比当前时间早超过该窗口。仅“每天启动一次任务”不保证满足 RPO，调度必须为上传、验证和重试预留时间。RTO 尚无经过演练证明的承诺；每次记录实际恢复耗时，不以容器启动时间代替业务恢复时间。

当前仓库尚无已启用的备份调度、异地目的地或已验证解密密钥。这三项和保留周期须在目标环境确认，不能报告备份能力已经投入运行。失败任务应告警，上一份有效备份在替代副本验证完成前不得删除。

## 执行前检查

1. 核对主机、授权维护窗口、实际 Compose 文件、发布标识、三项挂载的绝对路径、剩余磁盘空间和异地副本位置。源路径或容器不符合预期时停止，不自动创建替代目录。
2. 确认 MySQL 和 MongoDB 正常运行、没有正在执行的迁移，并记录要恢复的镜像摘要、架构、数据库版本、已应用迁移及发布校验和。物理备份只在匹配版本和兼容主机上恢复，不在恢复过程中顺便升级数据库。
3. 在受保护的恢复主机预先生成并验证加密密钥；备份主机只保留收件人公钥文件。安装和密钥配置均须按主机授权执行。不得把私钥、环境文件或数据库内容输出到聊天、Git 或 CI 日志。
4. 通过受限运维会话记录每张业务表的行数、关键关联孤儿数、图片文件数量和内容摘要、MongoDB 集合数量及记录数量。先停止所有业务写入后再采集基准；原始文件名和内容不得写进仓库。恢复验收比较同一批基准，而不是和已继续写入的生产库比较。

## 当前拓扑的加密冷备

当前单机部署允许经授权的维护窗口，使用停止写入及干净停库后的冷备，避免引入尚未配置的在线快照一致性机制。不能在数据库运行时直接打包其数据目录。MySQL 的物理备份要求遵循[官方备份说明](https://dev.mysql.com/doc/refman/8.4/en/backup-methods.html)，MongoDB 的文件备份一致性要求见[官方快照说明](https://www.mongodb.com/docs/manual/tutorial/backup-with-filesystem-snapshots/)。

以下是默认部署布局的示例。操作者须先核对实际路径；不一致时先审查调整，不能盲目运行。关闭公网旅游平台写入口只允许修改已确认的 LYW 路由，不停止或更改 Dify 自身服务。

```bash
set -euo pipefail
umask 077
command -v age >/dev/null
test -f /opt/lyw/compose.yaml
test -r /opt/lyw/secrets/backup-recipients.txt
test "$(realpath -e /opt/lyw/data)" = /opt/lyw/data
for part in mysql mongo uploads; do
  test -d "/opt/lyw/data/$part"
  test ! -L "/opt/lyw/data/$part"
done
docker compose -f /opt/lyw/compose.yaml config --quiet
docker compose -f /opt/lyw/compose.yaml stop lyw-frontend lyw-backend
```

确认所有业务写入者均停止后，采集上述基准，再干净停止数据库。下列状态检查失败时保留现场，不用强杀后生成的副本宣称冷备成功。

```bash
docker compose -f /opt/lyw/compose.yaml stop --timeout 120 lyw-mysql lyw-mongo
for container in lyw-mysql lyw-mongo; do
  test "$(docker inspect --format '{{.State.Status}}' "$container")" = exited
  test "$(docker inspect --format '{{.State.ExitCode}}' "$container")" = 0
  test "$(docker inspect --format '{{.State.OOMKilled}}' "$container")" = false
done
test "$(docker inspect --format '{{.State.Running}}' lyw-backend)" = false
test "$(docker inspect --format '{{.State.Running}}' lyw-frontend)" = false
mkdir -p /opt/lyw/backups
backup_batch=$(mktemp -d /opt/lyw/backups/lyw-backup-XXXXXXXX)
tar --numeric-owner -C /opt/lyw/data -cf - mysql mongo uploads |
  age -R /opt/lyw/secrets/backup-recipients.txt > "$backup_batch/data.tar.age.partial"
mv -- "$backup_batch/data.tar.age.partial" "$backup_batch/data.tar.age"
(cd "$backup_batch" && sha256sum data.tar.age > SHA256SUMS)
```

管道启用 `pipefail`，任何读取或加密失败都不能生成成功标记。加密接口依据 [age 官方用法](https://github.com/FiloSottile/age#usage)；该工具是运维前置依赖，不是本项目已自动安装的应用依赖。批次路径由独占临时目录生成，避免覆盖已有备份；不产生明文中间包。

把发布与基准信息放入同批受保护记录，复制密文和校验和到已批准的异地目的地，然后在持有解密私钥的恢复主机验证。校验和只发现传输损坏，不能代替可信来源和解密认证。加密成功也不等于可恢复，至少执行下节隔离验证。

任何步骤失败：保留上一份有效备份；记录精确批次及失败原因。失败密文不得列为可用备份，需要删除时先核对精确路径并取得适用授权。数据库和应用可能已停止，必须明确报告，不自动掩盖失败。确认原数据未损坏且维护恢复已获授权后，按“数据库 → 后端 → 前端”恢复原服务并检查健康；不为完成备份而执行任何 Dify 重启。

## 隔离恢复演练

只能在独立恢复主机或经确认的隔离环境进行。**不能直接使用生产 Compose 加 `-p` 当作隔离**：其中固定容器名和默认绑定目录仍可能指向原环境。恢复用配置必须去掉固定生产容器名，不接入 Dify 网络、不发布公网端口、仅挂载本批恢复目录，并关闭真实邮件及 AI 等外部副作用。

1. 使用受保护渠道接收密文、可信清单与私钥；校验发布和镜像版本。先验密文，再在新建的空目录解密。不要覆盖活跃数据目录。
2. 下列命令仅对由本手册生成且来源可信的备份执行。每个参数都要求操作者明确传入；私钥内容不放在参数中。完整解密先写入权限受限的隔离临时包，认证失败时不解包，避免部分明文被误用。

```bash
set -euo pipefail
umask 077
: "${LYW_BACKUP_BATCH:?提供已授权备份批次绝对目录}"
: "${LYW_RESTORE_PARENT:?提供已授权隔离恢复根目录}"
: "${LYW_RESTORE_IDENTITY_FILE:?提供受保护解密私钥文件路径}"
test -d "$LYW_RESTORE_PARENT"
test -r "$LYW_RESTORE_IDENTITY_FILE"
(cd "$LYW_BACKUP_BATCH" && sha256sum --check SHA256SUMS)
restore_batch=$(mktemp -d "$LYW_RESTORE_PARENT/lyw-restore-XXXXXXXX")
mkdir "$restore_batch/data"
age --decrypt -i "$LYW_RESTORE_IDENTITY_FILE" \
  -o "$restore_batch/data.tar" "$LYW_BACKUP_BATCH/data.tar.age"
tar --numeric-owner -xf "$restore_batch/data.tar" -C "$restore_batch/data"
rm -- "$restore_batch/data.tar"
```

仅在支持原 UID/GID 与文件权限的 Linux 环境操作。解密或解包失败时，该批恢复目录不能启动数据库；将精确路径登记为受保护的待清理项，不能把含明文的失败目录遗忘在主机上。删除临时包并不等于存储介质安全擦除，恢复磁盘本身应加密。

3. 启动匹配备份版本的隔离 MySQL 和 MongoDB，先验证数据，再提供全新的 Redis。物理恢复保留数据库原用户配置；单纯修改容器初始化环境变量不会修改已有数据库密码。
4. 核对全部业务表行数、主外关联、上传文件摘要与引用、MongoDB 集合记录数；偏差必须解释。版本或迁移不匹配时停止，不直接执行空库初始化 SQL。
5. 恢复旧数据库会恢复旧身份会话和链接。保持应用停写，执行[身份事件手册](identity-incident-response.md)的全会话与一次性链接失效流程，并使用新的签名密钥；不能把旧 JWT 密钥从备份恢复为当前密钥。
6. 启动后端和前端，验证内部健康、匿名只读浏览及经批准的隔离测试账户登录。检查使用的是假邮件/外部服务边界，不能向真实用户投递演练邮件。对照本批基准验证后，记录 RPO 与完整 RTO。
7. 演练通过不授权生产切换。生产切换需要独立确认目标、停写窗口、增量数据保留和回滚路径；回滚不能把新写入直接丢弃，也不能重新启用旧凭据。
8. 结束后停止并精确移除本批隔离容器、网络和恢复目录，回查无残留。备份密文本身按批准保留策略处理，不因演练结束自动删除。未清理项必须持久记录目标、批次路径、原因、风险、清理方法与状态；不将明文数据或秘密写入记录。

## 演练记录要求

每次在 `docs/runbooks/` 增加以日期和批次命名的脱敏记录，包含：授权范围、源发布与镜像摘要、备份时间、密文校验和、异地副本验证、隔离目标标识、恢复开始/结束、表与文件校验数量、差异、凭据失效证据、健康与旅程结果、实测 RPO/RTO、清理回查、待清理项和执行结论。敏感明细仅引用受保护证据的位置，不粘贴内容。

## 2026-08-31 文档核对记录

已对照当前 Compose 的实际服务、绑定目录和身份 Mapper 核对步骤；命令通过 Bash 语法检查，文件链接检查通过。本文备份和恢复命令没有执行，未创建本手册要求的备份、恢复目录或密钥；身份撤销 SQL 的独立隔离测试见[身份事件验证记录](identity-incident-response.md#验证与记录)。初次核对时 Docker 引擎不可用，后已修复并完成独立 MySQL 镜像的[迁移与身份复验](../data/identity-migration.md#2026-08-31-docker-恢复后的隔离复验)，但这不构成本手册的加密备份恢复演练。MinIO/OpenSearch、备份调度、异地复制、密钥保管以及实际恢复证据均未交付，不能以本文存在代替这些验收项。

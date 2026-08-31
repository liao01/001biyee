# 旅游分享平台 (Travel Sharing Platform)

这是一个基于现代化技术栈构建的旅游心得分享与社交平台。用户可以发布旅游帖子、分享美图、记录地理位置，并与其他旅游爱好者进行社交互动。系统深度集成了 AI 助手，为用户提供智能化的旅游建议。

## 🌐 在线演示

本分支包含尚未发布的邮箱身份升级，不代表线上环境已运行当前提交。线上入口、部署约束和回滚方式见[部署设计](docs/superpowers/specs/2026-08-30-tencent-lighthouse-constrained-deployment-design.md)；不要把历史 HTTP 演示地址作为新身份流程的生产验收依据。

## 🚀 项目模块

项目采用前后端分离架构，包含以下核心模块：

- **`business`**: 后端核心业务模块，基于 Spring Boot 3 构建。
- **`admin`**: 后台管理系统前端，基于 Vue 3 + Vite + Ant Design Vue。
- **`web`**: 用户门户前端，基于 Vue 3 + Vite，提供响应式的用户体验。
- **`generator`**: MyBatis 代码生成工具，用于快速生成持久层代码。

## 🛠 技术栈

### 后端
- **核心框架**: Spring Boot 3.x
- **持久层**: MyBatis + MyBatis Generator
- **数据库**: MySQL 8.0 (核心业务), MongoDB (聊天记录存储)
- **缓存**: Redis（可失效的统计等派生数据，不是正式身份会话事实源）
- **AI 集成**: LangChain4j (支持流式对话与外部大模型调用)
- **工具库**: Hutool, Lombok, Fastjson

### 前端
- **框架**: Vue 3 (Composition API)
- **构建工具**: Vite
- **状态管理**: Pinia / Vuex
- **UI 组件库**: Ant Design Vue / Element Plus
- **数据可视化**: ECharts
- **地图服务**: 高德地图 (Amap API)

## ✨ 核心功能

- **用户认证体系**: 
  - 邮箱与密码注册、登录，一次性邮件链接验证邮箱和重置密码；不使用短信验证。
  - 访问凭据只保存在用户端内存，刷新会话由安全 Cookie 传输。
  - 接口、兼容边界和已验证范围见[邮箱身份 HTTP 链路](docs/data/identity-http.md)。
- **内容发布系统**:
  - 旅游心得发布，支持多图上传、富文本内容、标签分类。
  - 自动提取图片地理信息或手动选择旅游地点。
- **社交互动**:
  - 帖子点赞、收藏功能。
  - 深度社交：用户关注系统、粉丝统计、关注趋势分析。
  - 多层级评论系统。
- **智能 AI 助手**:
  - **小智助手**: 支持流式 (SSE) 聊天，具备长期记忆功能。
  - **智能客服**: 基于大模型调用的客服系统，支持历史记录回溯。
- **后台管理与统计**:
  - 仪表盘数据可视化，实时监控用户增长与内容动态。
  - 完善的位置信息管理与内容审核工具。

## 📦 快速开始

### 环境准备

- JDK 17、Node.js 22；使用已提交的 Maven Wrapper 和 npm 锁文件。
- MySQL、Redis、MongoDB；容器版本与资源配置以 [deploy/compose.yaml](deploy/compose.yaml) 为准。
- 容器运行需要 Docker Engine 和 Docker Compose；只有 Compose CLI 而没有运行中的引擎，不能进行容器验收。

### 后端启动

1. 仅对确认无业务数据的空库使用 [sql/travel_share.sql](sql/travel_share.sql)。它包含删除并重建表的语句，不能用来升级已有库。旧账户升级遵循[邮箱迁移手册](docs/data/identity-migration.md)。
2. 以 [.env.example](.env.example) 为环境变量清单，通过本机环境或 Git 忽略的配置文件提供实际值。[application.properties.example](business/src/main/resources/application.properties.example) 展示配置接线；Spring 不会自动加载仓库根的 `.env` 文件。不得输出或提交实际凭据。
3. 本机开发时将上述 Spring 示例复制为同目录被忽略的 `application.properties`，再提供其引用的环境变量。启动前核对连接的是自己的开发库，而非生产或共享测试库。
4. 在 `business` 目录运行 `./mvnw spring-boot:run`（Windows 使用 `./mvnw.cmd spring-boot:run`）。

邮箱链接基础地址、生产 HTTPS 和本地 Cookie 配置遵循[身份部署要求](docs/data/identity-http.md#部署要求)。前端地址必须与配置的可信来源一致；`localhost` 和 `127.0.0.1` 不可混用。管理员认证是独立边界，不能用会员凭据代替。

### 前端启动
```bash
# 进入 web 或 admin 目录
npm ci
npm run dev
```

## 验证与运行手册

从仓库根运行 `python -m unittest discover -s tests -t . -v`。真实 MySQL 迁移测试会创建并精确清理带批次标识的隔离库；不要向运行器提供生产凭据。

在 `business` 目录运行 `./mvnw test`；在 `web` 目录运行 `npm test` 和 `npm run build`。真实邮箱 HTTP/Vue 联调从仓库根运行 `python -m scripts.run_backend_integration`，前置条件和清理策略见[身份测试说明](docs/data/identity-http.md#本地与-ci-验证)。

- [资源受限部署设计](docs/superpowers/specs/2026-08-30-tencent-lighthouse-constrained-deployment-design.md)：实际拓扑、构建、Nginx 入口和回滚边界。
- [备份与恢复](docs/runbooks/backup-and-restore.md)：加密冷备、隔离恢复、数据校验与演练记录。
- [身份事件响应](docs/runbooks/identity-incident-response.md)：刷新会话撤销、密钥轮换和强制重新登录。
- [安全说明](SECURITY.md)：秘密管理和提交前检查。

新旅行行程平台仍按 [Issue #12](https://github.com/liao01/001biyee/issues/12) 分阶段建设。当前 Compose 尚未包含目标架构中的 MinIO 和 OpenSearch，不能据现有部署声称完整平台已交付。远端 CI、生产 HTTPS/邮件和完整浏览器旅程的未验收项见[最新身份验证记录](docs/data/identity-http.md)。


# 旅游分享平台 (Travel Sharing Platform)

这是一个基于现代化技术栈构建的旅游心得分享与社交平台。用户可以发布旅游帖子、分享美图、记录地理位置，并与其他旅游爱好者进行社交互动。系统深度集成了 AI 助手，为用户提供智能化的旅游建议。

## 🌐 在线演示
- **演示地址**: [http://42.193.184.77/](http://42.193.184.77/)

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
- **缓存**: Redis (验证码与 Session 缓存)
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
  - 支持手机号注册、登录及密码重置。
  - 集成图片验证码与短信验证码，确保安全性。
  - 基于 JWT 的无状态认证拦截机制。
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
- JDK 17+
- MySQL 8.0+
- Redis
- MongoDB
- Maven 3.8+
- Node.js 16+

### 后端启动
1. 导入 `travel_share.sql` 到 MySQL 数据库。
2. 修改 `business/src/main/resources/application.yml` 中的数据库及中间件配置。
3. 运行 `com.jiawa.lyw.BusinessApplication` 启动后端服务。

### 前端启动
```bash
# 进入 web 或 admin 目录
npm install
npm run dev
```

## � 部署说明
该项目已部署于云服务器环境：
- **服务器 IP**: `42.193.184.77`
- **前端部署**: 建议使用 Nginx 进行反向代理及静态资源托管。
- **后端部署**: 通过 Maven 打包为 Jar 包，使用 `nohup` 或 `systemd` 运行。
- **静态资源**: 图片等上传资源映射至服务器本地目录 `D:/idea/lyw/uploads/` (Windows) 或相应 Linux 路径。

## �📄 开源协议
本项目遵循 MIT 开源协议。

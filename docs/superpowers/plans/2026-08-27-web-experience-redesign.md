# 旅分享用户端体验改版实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留现有业务接口和状态模型的前提下，将全部用户端页面统一为已确认的旅行内容产品体验。

**Architecture:** 先建立设计变量和统一应用外壳，再重构内容浏览链路、用户空间页面和旅行工具页面。页面组件继续负责现有业务请求，共用视觉组件只接收展示数据和事件，避免产生新的业务事实源。

**Tech Stack:** Vue 3、Vue Router、Vuex、Pinia、Ant Design Vue、Vite、Vitest

**Spec:** `docs/superpowers/specs/2026-08-27-web-experience-redesign.md`

## Global Constraints

- 不修改现有后端接口、数据库字段、认证协议和管理端页面。
- 主背景必须保持纯白 `#ffffff`，品牌强调色为 `#ff3b4f`。
- 桌面端使用统一顶部栏与侧栏；移动端小于 `768px` 切换为单列和底部导航。
- 现有登录、搜索、点赞、收藏、关注、评论、发布、地图和 AI 行为必须保留。
- 不执行 Git 提交、推送或 worktree 操作。

---

### Task 1: 设计系统与应用外壳

**Files:**
- Create: `web/src/styles/tokens.css`
- Create: `web/src/styles/components.css`
- Modify: `web/src/style.css`
- Modify: `web/src/view/home.vue`
- Modify: `web/src/components/the-header.vue`
- Modify: `web/src/components/the-sider.vue`
- Test: `web/src/components/AppShell.test.js`

**Interfaces:**
- Produces: CSS 变量 `--travel-color-*`、`--travel-space-*`、`--travel-radius-*`；页面容器类 `.travel-page`、`.travel-page__header`、`.travel-panel`。

- [ ] **Step 1: 写应用外壳测试**

验证导航标签、桌面内容容器和移动导航入口存在，并验证受限入口仍调用登录门禁。

- [ ] **Step 2: 运行测试并确认旧结构不满足统一外壳约束**

Run: `npm test -- AppShell.test.js`

- [ ] **Step 3: 建立设计变量与共用基础样式**

定义颜色、排版、间距、圆角、边框、动效和响应式断点；全局样式只提供视觉事实源，不复制业务枚举。

- [ ] **Step 4: 重构顶部栏、侧栏和首页布局**

保留 `showSearch`、登录弹窗、心跳、用户菜单和路由跳转；将固定尺寸和行内样式迁移到语义类名。

- [ ] **Step 5: 运行外壳测试与构建**

Run: `npm test -- AppShell.test.js && npm run build`

### Task 2: 内容发现与帖子详情

**Files:**
- Create: `web/src/components/travel/PostPreview.vue`
- Modify: `web/src/view/page/cardlist.vue`
- Modify: `web/src/modules/post-detail/PostDetail.vue`
- Test: `web/src/modules/post-detail/PostDetail.test.js`
- Test: `web/src/view/page/PostDetailSources.test.js`

**Interfaces:**
- Produces: `PostPreview` props `post`, emits `open`；详情页继续消费 `postDetailHttp` 与 `postDetailNavigation`。

- [ ] **Step 1: 扩展现有测试覆盖标题、作者、地点、图片和互动入口**

- [ ] **Step 2: 运行测试并记录失败状态**

Run: `npm test -- PostDetail.test.js PostDetailSources.test.js`

- [ ] **Step 3: 重构发现页内容层级和帖子预览组件**

保留现有搜索 store、分页请求和详情打开行为；增加标签导航、稳定图片比例、元信息和统一加载/空状态。

- [ ] **Step 4: 按已确认概念重构详情阅读布局**

保留详情请求、浏览记录、点赞、收藏、关注和评论接口；桌面端形成阅读列与互动栏，移动端回落为单列。

- [ ] **Step 5: 运行内容链路测试与构建**

Run: `npm test -- PostDetail.test.js PostDetailSources.test.js && npm run build`

### Task 3: 个人空间、作者与内容列表页

**Files:**
- Create: `web/src/components/travel/ProfileHeader.vue`
- Modify: `web/src/view/page/UserProfile.vue`
- Modify: `web/src/view/page/UserDetail.vue`
- Modify: `web/src/view/page/AuthorDetail.vue`
- Modify: `web/src/view/page/FavoriteList.vue`
- Modify: `web/src/view/page/cardlistView.vue`
- Modify: `web/src/view/page/PostHistory.vue`
- Modify: `web/src/view/page/Userfollow.vue`
- Test: `web/src/view/page/AuthorDetail.test.js`

**Interfaces:**
- Produces: `ProfileHeader` props `profile`, `isSelf`, `following`，emits `edit`、`publish`、`follow`。

- [ ] **Step 1: 扩展作者页测试覆盖身份区和关注状态**

- [ ] **Step 2: 运行测试并确认失败**

Run: `npm test -- AuthorDetail.test.js`

- [ ] **Step 3: 建立共用身份区并重构个人资料与作者主页**

继续使用各页面现有数据请求；相同身份信息使用 `ProfileHeader`，自有页面和他人页面通过显式 props 区分。

- [ ] **Step 4: 统一收藏、浏览历史、发布历史和粉丝数据页面**

复用页面头、筛选、列表、空状态和分页样式，不合并各页面不同的接口或业务状态。

- [ ] **Step 5: 运行用户空间测试与构建**

Run: `npm test -- AuthorDetail.test.js && npm run build`

### Task 4: 发布、地图与旅游助手

**Files:**
- Modify: `web/src/view/page/upload-post.vue`
- Modify: `web/src/view/page/map.vue`
- Modify: `web/src/view/page/ai2.vue`
- Modify: `web/src/view/page/ai.vue`

**Interfaces:**
- Consumes: Task 1 的设计变量与应用外壳。
- Produces: 统一的表单、工具栏、消息气泡、地图覆盖控件和响应式工具页布局。

- [ ] **Step 1: 为发布流程补充最小结构测试或使用现有构建作为回归门禁**

- [ ] **Step 2: 重构发布页为分区清晰的编辑表单**

保留现有上传、地点选择、校验和提交逻辑，仅调整结构、控件文案层级和反馈样式。

- [ ] **Step 3: 重构地图页为全高工具画布**

保留地图 SDK 初始化、标记和弹层逻辑；把筛选与地点信息放入统一的浮动工具面板。

- [ ] **Step 4: 统一 AI 助手页面**

以 `ai2.vue` 为正式入口风格，保留现有 SSE、会话和登录门禁；旧 `ai.vue` 使用同一视觉变量，不新增第三套交互模型。

- [ ] **Step 5: 运行全量测试和构建**

Run: `npm test && npm run build`

### Task 5: 浏览器视觉验收

**Files:**
- No repository file changes unless QA discovers defects.

**Interfaces:**
- Consumes: 已确认概念图和所有实现页面。

- [ ] **Step 1: 启动本地前端并检查发现、详情和个人空间桌面视图**

Run: `npm run dev -- --host 127.0.0.1`

- [ ] **Step 2: 检查 390px 移动视图与键盘焦点状态**

- [ ] **Step 3: 对比概念和实现，至少记录布局、排版、颜色、图片、容器、图标、响应式七项**

- [ ] **Step 4: 修复可见差异并重新运行测试与构建**

- [ ] **Step 5: 删除临时 QA 产物并报告剩余偏差**

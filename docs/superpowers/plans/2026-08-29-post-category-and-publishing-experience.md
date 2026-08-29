# 帖子分类与发布体验收敛 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立数据库驱动的帖子正式分类，修复发布历史，并按已确认的 A 方案收敛详情、发布、发现卡片和重复入口。

**Architecture:** `post_category` 与 `post.category_code` 构成唯一分类事实源，后端统一向发布、列表、搜索、详情和历史提供分类契约；Vue 页面只消费接口返回的分类。现有帖子详情 deep module 和普通标签兼容层保留，页面改动围绕共享预览模型与应用外壳收敛。

**Tech Stack:** MySQL 8、Java 17、Spring Boot、MyBatis、JUnit 5/Mockito/MockMvc、Vue 3、Vite、Vitest、Vue Test Utils、Ant Design Vue。

**Spec:** `docs/superpowers/specs/2026-08-29-post-category-and-publishing-experience-design.md`

## Global Constraints

- 正式分类仅为 `CITY_WALK`、`NATURAL_SCENERY`、`FOOD`；“推荐、最新”不进入帖子数据。
- 分类名称与启用状态只从 `post_category` 读取，前端不复制中文分类列表。
- 新发布内容必须选择一个启用分类；旧标签仅作为迁移输入和普通标签兼容层。
- 详情继续以 `postId` 为唯一入口，发布作者继续来自 `LoginMemberContext`。
- 不创建子代理或 worktree，不执行提交、推送、合并或未确认的数据库写入。
- 每个生产行为先写失败测试并确认失败原因，再写最小实现。
- 数据迁移先 dry-run，执行模式必须报告未分类和冲突帖子，且不得删除帖子或旧标签。

---

## File Structure

- `sql/migrations/20260829_post_categories.sql`：分类建表、字段收敛、dry-run、迁移和校验统计。
- `business/.../domain/PostCategory.java`：分类领域对象。
- `business/.../mapper/PostCategoryMapper.java` 与 `resources/mapper/PostCategoryMapper.xml`：分类查询边界。
- `business/.../service/PostCategoryService.java`：启用分类查询与分类校验。
- `business/.../resp/PostCategoryResp.java`：分类接口响应。
- `business/.../req/PostReq.java`、`domain/Post.java`、`mapper/PostMapper.xml`：保存正式分类。
- `business/.../resp/PostResp.java`、`PostUserResp.java`、`PostDetailResp.java`、`mapper/Cust/PostMapperCust.xml`：统一返回分类。
- `web/src/api/postCategories.js`：分类请求与发现查询参数构造。
- `web/src/view/page/cardlist.vue`、`upload-post.vue`、`PostHistory.vue`：发现、发布、历史页面行为。
- `web/src/components/travel/PostPreview.vue`、`postPreviewAdapter.js`：统一卡片分类展示。
- `web/src/modules/post-detail/PostDetail.vue`：A 方案详情双栏布局。
- `web/src/components/the-header.vue`、`ProfileHeader.vue`、`view/page/UserProfile.vue`：重复入口收敛。

### Task 1: 数据库分类事实源与可审计迁移

**Files:**
- Create: `sql/migrations/20260829_post_categories.sql`
- Modify: `sql/travel_share.sql`
- Create: `tests/scripts/test_post_category_migration.py`
- Create: `tests/scripts/mysql_migration_harness.py`

**Interfaces:**
- Produces: `post_category(code, name, sort_order, enabled, create_time, update_time)`。
- Produces: 可空 `post.category_code`，外键指向 `post_category.code`。
- Test utility: `temporary_schema(dsn)`, `run_sql_script(connection, path, apply)`, `fetch_scalar(connection, sql, args)`，只操作名称带随机后缀的 `lyw_post_category_migration_test_*` 数据库并在测试结束时精确删除该数据库。

- [x] **Step 1: 写迁移行为失败测试**

测试连接由 `LYW_MIGRATION_TEST_DSN` 指向的隔离 MySQL 测试库，创建最小 `post/tag/post_tag` fixture，实际执行迁移两次并断言结果相同。fixture 包含可唯一映射、冲突和未映射帖子；断言三项分类、映射结果、冲突/未映射残留以及旧帖子和旧标签均仍存在。

```python
def test_migration_is_idempotent_and_preserves_ambiguous_history(mysql_schema):
    seed_legacy_posts(mysql_schema)
    first_report = run_sql_script(mysql_schema, MIGRATION_PATH, apply=True)
    second_report = run_sql_script(mysql_schema, MIGRATION_PATH, apply=True)

    assert fetch_category_codes(mysql_schema) == {
        "CITY_WALK", "NATURAL_SCENERY", "FOOD"
    }
    assert fetch_post_category(mysql_schema, 101) == "FOOD"
    assert fetch_post_category(mysql_schema, 102) == "NATURAL_SCENERY"
    assert fetch_post_category(mysql_schema, 103) == "CITY_WALK"
    assert fetch_post_category(mysql_schema, 104) is None
    assert first_report.unmapped_count == second_report.unmapped_count == 1
    assert count_rows(mysql_schema, "post") == 4
    assert count_rows(mysql_schema, "tag") == 5
```

- [x] **Step 2: 运行测试确认因迁移文件不存在而失败**

Run: `python -m unittest tests.scripts.test_post_category_migration -v`

Expected: FAIL，原因是迁移文件或迁移执行 helper 尚不存在；如果隔离测试库凭据不可用，停止本任务并报告所需的 `LYW_MIGRATION_TEST_DSN`，不改用共享数据库。

- [x] **Step 3: 实现可重复迁移和 schema 快照**

迁移使用 `CREATE TABLE IF NOT EXISTS`、`INSERT ... ON DUPLICATE KEY UPDATE`，通过 `information_schema.columns` 和预备语句只在缺失时添加 `post.category_code`。映射规则严格为：`美食 -> FOOD`、`景点 -> NATURAL_SCENERY`、仅含 `旅行/攻略` 且无更具体分类时 `CITY_WALK`；冲突和未匹配记录保持空值并输出 ID。

- [x] **Step 4: 运行迁移测试和安全扫描**

Run: `python -m unittest tests.scripts.test_post_category_migration tests.scripts.security.test_scan_repository -v`

Expected: PASS，且扫描输出无秘密或危险删除语句告警。

- [x] **Step 5: 检查变更检查点**

Run: `git diff --check -- sql/migrations/20260829_post_categories.sql sql/travel_share.sql tests/scripts/test_post_category_migration.py tests/scripts/mysql_migration_harness.py`

Expected: 无输出。未经授权不提交。

### Task 2: 后端分类查询与校验边界

**Files:**
- Create: `business/src/main/java/com/jiawa/lyw/domain/PostCategory.java`
- Create: `business/src/main/java/com/jiawa/lyw/mapper/PostCategoryMapper.java`
- Create: `business/src/main/resources/mapper/PostCategoryMapper.xml`
- Create: `business/src/main/java/com/jiawa/lyw/resp/PostCategoryResp.java`
- Create: `business/src/main/java/com/jiawa/lyw/service/PostCategoryService.java`
- Test: `business/src/test/java/com/jiawa/lyw/service/PostCategoryServiceTests.java`

**Interfaces:**
- Produces: `List<PostCategoryResp> listEnabled()`。
- Produces: `PostCategory requireEnabled(String code)`，不存在或停用时抛业务异常。

- [x] **Step 1: 写分类排序与拒绝非法编码的失败测试**

```java
@Test
void listEnabledShouldReturnDatabaseOrderAndRejectDisabledCodes() {
    when(mapper.selectEnabled()).thenReturn(List.of(
        category("CITY_WALK", "城市漫游", 10, true),
        category("FOOD", "美食", 30, true)));
    assertEquals(List.of("CITY_WALK", "FOOD"),
        service.listEnabled().stream().map(PostCategoryResp::getCode).toList());
    when(mapper.selectByCode("DISABLED")).thenReturn(category("DISABLED", "停用", 99, false));
    assertThrows(BusinessException.class, () -> service.requireEnabled("DISABLED"));
}
```

- [x] **Step 2: 运行测试确认缺少分类边界而失败**

Run: `mvn -pl business -Dtest=PostCategoryServiceTests test`

Expected: FAIL，原因是分类类与服务尚不存在。

- [x] **Step 3: 实现领域对象、Mapper 和服务**

`selectEnabled()` 的 SQL 固定为 `WHERE enabled = 1 ORDER BY sort_order, code`；`selectByCode` 使用显式 `@Param("code")`。

- [x] **Step 4: 运行服务测试**

Run: `mvn -pl business -Dtest=PostCategoryServiceTests test`

Expected: PASS。

### Task 3: 分类接口与发布请求契约

**Files:**
- Modify: `business/src/main/java/com/jiawa/lyw/controller/web/PostController.java`
- Modify: `business/src/main/java/com/jiawa/lyw/req/PostReq.java`
- Modify: `business/src/main/java/com/jiawa/lyw/service/PostService.java`
- Modify: `business/src/main/java/com/jiawa/lyw/domain/Post.java`
- Modify: `business/src/main/resources/mapper/PostMapper.xml`
- Test: `business/src/test/java/com/jiawa/lyw/controller/web/PostCategoryControllerTests.java`
- Test: `business/src/test/java/com/jiawa/lyw/service/PostServiceCategoryTests.java`

**Interfaces:**
- Consumes: `PostCategoryService.listEnabled()` 与 `requireEnabled(String)`。
- Produces: `GET /web/post/categories`。
- Produces: `PostReq.categoryCode: String`。

- [x] **Step 1: 写接口和保存行为失败测试**

MockMvc 断言分类接口返回稳定编码；服务测试构造伪造 `userId/status` 的请求，断言保存对象仍使用登录用户、公开状态和请求中的合法 `categoryCode`。

```java
assertEquals(100L, insertedPost.get().getUserId());
assertEquals(PostStatusEnum.OPEN.getCode(), insertedPost.get().getStatus());
assertEquals("CITY_WALK", insertedPost.get().getCategoryCode());
```

- [x] **Step 2: 运行两个测试确认失败**

Run: `mvn -pl business -Dtest=PostCategoryControllerTests,PostServiceCategoryTests test`

Expected: FAIL，原因是接口、字段和校验尚不存在。

- [x] **Step 3: 实现接口与保存路径**

在进入图片写入前调用 `requireEnabled(req.getCategoryCode())`；`PostMapper.xml` 的 insert、select 和 update 显式包含 `category_code`。保留事务注解并删除控制器中的 `System.out.println(req.getUserId())`。

- [x] **Step 4: 运行分类与保存测试**

Run: `mvn -pl business -Dtest=PostCategoryControllerTests,PostServiceCategoryTests test`

Expected: PASS。

### Task 4: 统一列表、详情与发布历史分类响应

**Files:**
- Modify: `business/src/main/java/com/jiawa/lyw/resp/PostResp.java`
- Modify: `business/src/main/java/com/jiawa/lyw/resp/PostUserResp.java`
- Modify: `business/src/main/java/com/jiawa/lyw/resp/PostDetailResp.java`
- Modify: `business/src/main/java/com/jiawa/lyw/service/PostDetailService.java`
- Modify: `business/src/main/java/com/jiawa/lyw/mapper/PostMapperCust.java`
- Modify: `business/src/main/resources/mapper/Cust/PostMapperCust.xml`
- Test: `business/src/test/java/com/jiawa/lyw/controller/web/PostDetailControllerTests.java`
- Create: `business/src/test/java/com/jiawa/lyw/service/PostHistoryQueryTests.java`

**Interfaces:**
- Produces: `categoryCode`、`categoryName` on `PostResp`/`PostUserResp`。
- Produces: `PostDetailResp.PostContent.categoryCode/categoryName`。

- [x] **Step 1: 扩展失败测试以要求分类返回和 `userId` 参数一致**

```java
mockMvc.perform(get("/web/post/detail").param("postId", "42"))
    .andExpect(jsonPath("$.content.post.categoryCode").value("NATURAL_SCENERY"))
    .andExpect(jsonPath("$.content.post.categoryName").value("自然风光"));
verify(postMapperCust).selectPostDetailsByUserId(100L);
```

- [x] **Step 2: 运行测试确认响应缺字段且历史绑定保护缺失**

Run: `mvn -pl business -Dtest=PostDetailControllerTests,PostHistoryQueryTests test`

Expected: FAIL。

- [x] **Step 3: 修改查询和响应映射**

所有帖子查询 `LEFT JOIN post_category pc ON pc.code = p.category_code`，选择 `p.category_code AS category_code` 与 `pc.name AS category_name`。将 `selectPostDetailsByUserId(@Param("userId") Long userId)` 与 XML `#{userId}` 对齐；同文件的用户参数方法同步使用明确名称。

- [x] **Step 4: 运行后端帖子相关测试**

Run: `mvn -pl business -Dtest='*Post*Tests' test`

Expected: PASS。

### Task 5: 前端分类 API、预览模型与发现筛选

**Files:**
- Create: `web/src/api/postCategories.js`
- Create: `web/src/api/postCategories.test.js`
- Modify: `web/src/components/travel/postPreviewAdapter.js`
- Modify: `web/src/components/travel/postPreviewAdapter.test.js`
- Modify: `web/src/components/travel/PostPreview.vue`
- Modify: `web/src/components/travel/PostPreview.test.js`
- Modify: `web/src/view/page/cardlist.vue`
- Create: `web/src/view/page/cardlist.test.js`

**Interfaces:**
- Produces: `fetchPostCategories(http)`。
- Produces: `buildDiscoveryParams({ view, categoryCode })` 返回后端查询参数。
- Produces: 预览模型 `categoryCode/categoryName`。

- [ ] **Step 1: 写失败测试**

```js
expect(toPostPreview({
  postId: '42', categoryCode: 'FOOD', categoryName: '美食', imageUrls: '/food.jpg'
}, { baseUrl: 'http://localhost/lyw' })).toEqual(expect.objectContaining({
  categoryCode: 'FOOD', categoryName: '美食'
}))
expect(buildDiscoveryParams({ view: 'LATEST', categoryCode: 'FOOD' }))
  .toEqual({ view: 'LATEST', categoryCode: 'FOOD' })
```

组件测试点击“最新”和“美食”，断言查询参数分别为 `{view:'LATEST'}` 和 `{categoryCode:'FOOD'}`，卡片渲染“美食”而非“旅行记录”。

- [ ] **Step 2: 运行定向测试确认失败**

Run: `npm test -- src/api/postCategories.test.js src/components/travel/postPreviewAdapter.test.js src/components/travel/PostPreview.test.js src/view/page/cardlist.test.js`

Workdir: `web`

Expected: FAIL，原因是 API helper、分类字段和筛选行为不存在。

- [ ] **Step 3: 实现分类加载、视图组合和卡片分类**

发现页本地只定义 `{key:'RECOMMENDED',label:'推荐'}` 与 `{key:'LATEST',label:'最新'}`；数据库分类由接口追加。分类为空时卡片显示“待分类”。

- [ ] **Step 4: 运行定向测试**

Run: `npm test -- src/api/postCategories.test.js src/components/travel/postPreviewAdapter.test.js src/components/travel/PostPreview.test.js src/view/page/cardlist.test.js`

Workdir: `web`

Expected: PASS。

### Task 6: 发布页分类行为与 A 方案双栏布局

**Files:**
- Modify: `web/src/view/page/upload-post.vue`
- Create: `web/src/view/page/upload-post.test.js`

**Interfaces:**
- Consumes: `fetchPostCategories(http)`。
- Produces: 发布 payload `{ title, content, categoryCode, images }`。

- [ ] **Step 1: 写发布行为失败测试**

挂载页面后返回三项分类，断言无分类或无图片时发布按钮禁用；选择 `CITY_WALK` 并填写内容、加入图片后提交一次，payload 不包含 `userId`、`status`、`locationId`、`tags`。

```js
expect(axios.post).toHaveBeenCalledWith(
  expect.stringContaining('/post-save'),
  expect.objectContaining({ categoryCode: 'CITY_WALK' }),
  expect.any(Object)
)
expect(axios.post.mock.calls[0][1]).not.toHaveProperty('userId')
```

- [ ] **Step 2: 运行测试确认旧页面提交标签和冗余字段而失败**

Run: `npm test -- src/view/page/upload-post.test.js`

Workdir: `web`

Expected: FAIL。

- [ ] **Step 3: 实现发布行为和双栏结构**

模板分为 `.publish-editor__main` 与 `.publish-editor__settings`；右栏使用原生可访问单选组或 Ant Design Radio，pending 时按钮禁用。分类加载失败显示重试按钮并禁用发布。

- [ ] **Step 4: 运行发布页测试**

Run: `npm test -- src/view/page/upload-post.test.js`

Workdir: `web`

Expected: PASS。

### Task 7: 详情 A 方案双栏与分类展示

**Files:**
- Modify: `web/src/modules/post-detail/PostDetail.vue`
- Modify: `web/src/modules/post-detail/PostDetail.test.js`
- Modify: `web/src/modules/post-detail/postDetailHttp.js`
- Modify: `web/src/modules/post-detail/postDetailHttp.test.js`

**Interfaces:**
- Consumes: `detail.post.categoryCode/categoryName`。
- Preserves: 点赞、收藏、关注、评论、浏览记录与迟到响应门禁。

- [ ] **Step 1: 写结构失败测试**

```js
expect(wrapper.get('[role="dialog"]').classes()).toContain('post-detail--two-column')
expect(wrapper.get('.post-detail__category').text()).toBe('自然风光')
expect(wrapper.get('.post-detail__gallery').exists()).toBe(true)
expect(wrapper.get('.post-detail__reader').exists()).toBe(true)
```

- [ ] **Step 2: 运行详情测试确认旧结构不符合双栏契约**

Run: `npm test -- src/modules/post-detail/PostDetail.test.js src/modules/post-detail/postDetailHttp.test.js`

Workdir: `web`

Expected: FAIL，且原有互动行为测试仍提供回归基线。

- [ ] **Step 3: 实现双栏布局和移动断点**

弹层宽度 `min(1360px, calc(100vw - 40px))`、高度上限 `92vh`；内容网格 `minmax(0, 58fr) minmax(360px, 42fr)`，小于 `768px` 单列。关闭按钮保持固定可见，内部单一滚动容器，分类位于标题前。

- [ ] **Step 4: 运行全部详情测试**

Run: `npm test -- src/modules/post-detail`

Workdir: `web`

Expected: PASS。

### Task 8: 重复入口与发布历史页面收敛

**Files:**
- Modify: `web/src/components/the-header.vue`
- Modify: `web/src/components/the-header.test.js`
- Modify: `web/src/components/travel/ProfileHeader.vue`
- Modify: `web/src/components/travel/ProfileHeader.test.js`
- Modify: `web/src/view/page/UserProfile.vue`
- Modify: `web/src/view/page/PostHistory.vue`
- Create: `web/src/view/page/PostHistory.test.js`

**Interfaces:**
- Preserves: 侧栏 `/uploadPost` 和 `/UserProfile` 主入口。
- Produces: 发布历史通过 `postId` 打开共享详情并使用 `postId` 行键。

- [ ] **Step 1: 写重复入口和历史状态失败测试**

顶部测试断言不存在 `.travel-header__publish` 和“个人资料”菜单项；个人头部断言不再发出 `publish`。历史测试模拟成功、失败、分页和标题点击，断言错误不是空列表、详情收到正确 `postId`。

- [ ] **Step 2: 运行测试确认旧入口和历史行为导致失败**

Run: `npm test -- src/components/the-header.test.js src/components/travel/ProfileHeader.test.js src/view/page/PostHistory.test.js`

Workdir: `web`

Expected: FAIL。

- [ ] **Step 3: 删除重复入口并修正历史页面**

移除顶部发布按钮、账户菜单“个人资料”、个人页 `@publish` 与 `goToPublish`；历史分页初始页改为 1，`rowKey="postId"`，标题按钮打开共享详情，错误状态单独显示。

- [ ] **Step 4: 运行定向测试**

Run: `npm test -- src/components/the-header.test.js src/components/travel/ProfileHeader.test.js src/view/page/PostHistory.test.js`

Workdir: `web`

Expected: PASS。

### Task 9: 完整验证、视觉校对与分支清理

**Files:**
- Modify only if verification reveals a concrete defect in files already listed above.
- Remove after QA: `.superpowers/brainstorm/481-1787970462/`。

**Interfaces:**
- Consumes: 全部后端与前端改动。
- Produces: 可复核的测试、构建、浏览器截图和分支清理结果。

- [ ] **Step 1: 运行完整自动化验证**

Run: `mvn -pl business test`

Run: `npm test`

Run: `npm run build`

Workdir for npm commands: `web`

Expected: 三项全部 PASS，输出无未处理错误。

- [ ] **Step 2: 启动应用并执行浏览器核心流程**

验证发现页推荐/最新/三个分类、卡片详情、发布表单、发布历史、个人页入口；桌面检查 `1440px` 和用户截图尺寸，移动检查小于 `768px`。

- [ ] **Step 3: 视觉忠实度账本**

并排检查已确认 A 方案视觉稿与最终截图，至少记录：双栏比例、标题首屏可见、分类位置、发布按钮唯一、图片裁切、评论滚动、移动单列和无横向溢出。可修复差异必须继续调整后重拍。

- [ ] **Step 4: 清理临时视觉伴随文件并检查工作区**

精确删除 `.superpowers/brainstorm/481-1787970462/`，确认没有临时截图、测试数据或运行进程残留。不得递归删除工作区根目录。

- [ ] **Step 5: 删除已确认的多余分支**

再次核验 `git branch --merged master`、`git worktree list --porcelain` 和远端祖先关系。仅删除已确认的本地 `codex/20260828-sanitized-integration` 与远端 `origin/codex/20260826-post-detail-migration`；不删除当前分支、未合并分支或 worktree 正在使用的分支。删除后重新列出本地与远端分支确认结果。

- [ ] **Step 6: 最终 diff 与安全检查点**

Run: `git diff --check`

Run: `git status --short`

Run: `python scripts/security/scan_repository.py`

Expected: 无格式错误、无秘密或高危安全阻塞；工作区只包含本任务预期改动。未经授权不提交或推送。

import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import PostDetail from './PostDetail.vue'
import { postDetailHttpKey } from './postDetailHttp.js'
import { postDetailNavigationKey } from './postDetailNavigation.js'

describe('帖子详情', () => {
  it('按 postId 打开并展示公共详情', async () => {
    const http = {
      async getPublicDetail(postId) {
        if (postId !== '42') throw new Error(`unexpected postId: ${postId}`)
        return {
          post: {
            id: '42',
            title: '桂林山水之旅',
            description: '沿着漓江慢慢看山水。',
            postTime: '2026-08-20T10:00:00Z',
          },
          author: {
            id: '7',
            name: '旅行者小林',
            avatar: '/uploads/avatar/xiaolin.png',
          },
          images: ['/uploads/guilin.jpg'],
          comments: [
            {
              id: '501',
              userId: '8',
              membername: '山水读者',
              avatar: '/uploads/avatar/reader.png',
              commentContent: '景色真漂亮',
              commentTime: '2026-08-21T09:00:00Z',
            },
          ],
          interactionCounts: {
            like: 12,
            favorite: 4,
          },
        }
      },
      async getViewerState() {
        return {
          liked: false,
          favorited: false,
          followed: false,
          selfAuthor: false,
        }
      },
    }

    const wrapper = mount(PostDetail, {
      props: { open: false, postId: null },
      global: { provide: { [postDetailHttpKey]: http } },
    })

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)

    await wrapper.setProps({ open: true, postId: '42' })
    await flushPromises()

    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.text()).toContain('桂林山水之旅')
    expect(dialog.text()).toContain('旅行者小林')
    expect(dialog.text()).toContain('沿着漓江慢慢看山水。')
    expect(dialog.get('img[alt="桂林山水之旅 图片 1"]').attributes('src'))
      .toBe('/uploads/guilin.jpg')
    expect(dialog.text()).toContain('山水读者')
    expect(dialog.text()).toContain('景色真漂亮')
    const comment = dialog.get('li[data-comment-id="501"]')
    expect(comment.get('img[alt="山水读者的头像"]').attributes('src'))
      .toBe('/uploads/avatar/reader.png')
    expect(comment.get('time').attributes('datetime')).toBe('2026-08-21T09:00:00Z')
  })

  it('通过受控 open interface 关闭详情', async () => {
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '桂林山水之旅', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 0, favorite: 0 },
        }
      },
      async getViewerState() {
        return { liked: false, favorited: false, followed: false, selfAuthor: false }
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    await wrapper.get('button[aria-label="关闭帖子详情"]').trigger('click')

    expect(wrapper.emitted('update:open')).toEqual([[false]])
    await wrapper.setProps({ open: false })
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('展示服务端读取到的基础互动状态', async () => {
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '桂林山水之旅', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        return { liked: true, favorited: false, followed: true, selfAuthor: false }
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    const like = wrapper.get('button[aria-label="点赞"]')
    expect(like.attributes('aria-pressed')).toBe('true')
    expect(like.text()).toContain('12')

    const favorite = wrapper.get('button[aria-label="收藏"]')
    expect(favorite.attributes('aria-pressed')).toBe('false')
    expect(favorite.text()).toContain('4')

    const follow = wrapper.get('button[aria-label="关注作者"]')
    expect(follow.attributes('aria-pressed')).toBe('true')
    expect(follow.text()).toBe('已关注')
  })

  it('登录者状态失败时仍允许匿名阅读公共详情', async () => {
    const http = {
      async getPublicDetail() {
        return {
          post: {
            id: '42',
            title: '匿名也能阅读的旅行',
            description: '公共内容不依赖登录状态。',
          },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        throw new Error('unauthorized')
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    expect(wrapper.get('[role="dialog"]').text()).toContain('匿名也能阅读的旅行')
    expect(wrapper.get('[role="dialog"]').text()).toContain('公共内容不依赖登录状态。')
    expect(wrapper.get('[role="status"]').text()).toBe('登录后可查看个人互动状态')
  })

  it('匿名读者触发互动时得到登录提示且不会发送写请求', async () => {
    let likeWrites = 0
    const showLogin = vi.fn()
    window.showLogin = showLogin
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '匿名可读帖子', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        throw new Error('unauthorized')
      },
      async setLike() {
        likeWrites += 1
        return { active: true, count: 13 }
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    await wrapper.get('button[aria-label="点赞"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="status"]').text()).toContain('请先登录')
    expect(showLogin).toHaveBeenCalledOnce()
    expect(likeWrites).toBe(0)
    delete window.showLogin
  })

  it('点赞成功后只采用服务端确认的 active 状态和正式计数', async () => {
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '服务端确认互动', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        return { liked: false, favorited: false, followed: false, selfAuthor: false }
      },
      async setLike(postId, active) {
        expect(postId).toBe('42')
        expect(active).toBe(true)
        return { active: true, count: 99 }
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    await wrapper.get('button[aria-label="点赞"]').trigger('click')
    await flushPromises()

    const like = wrapper.get('button[aria-label="点赞"]')
    expect(like.attributes('aria-pressed')).toBe('true')
    expect(like.text()).toContain('99')
  })

  it('点赞写入期间禁用按钮且快速重复点击最多发送一个请求', async () => {
    let resolveLike
    let writes = 0
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '防止重复互动', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        return { liked: false, favorited: false, followed: false, selfAuthor: false }
      },
      setLike() {
        writes += 1
        return new Promise((resolve) => { resolveLike = resolve })
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    const like = wrapper.get('button[aria-label="点赞"]')
    await like.trigger('click')
    await like.trigger('click')

    expect(like.attributes('disabled')).toBeDefined()
    expect(writes).toBe(1)

    resolveLike({ active: true, count: 13 })
    await flushPromises()
    expect(wrapper.get('button[aria-label="点赞"]').attributes('disabled')).toBeUndefined()
  })

  it('收藏成功后采用服务端确认状态和计数', async () => {
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '可靠收藏', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        return { liked: false, favorited: false, followed: false, selfAuthor: false }
      },
      async setFavorite(postId, active) {
        expect(postId).toBe('42')
        expect(active).toBe(true)
        return { active: true, count: 77 }
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    await wrapper.get('button[aria-label="收藏"]').trigger('click')
    await flushPromises()

    const favorite = wrapper.get('button[aria-label="收藏"]')
    expect(favorite.attributes('aria-pressed')).toBe('true')
    expect(favorite.text()).toContain('77')
  })

  it('关注作者后采用服务端确认的关系状态', async () => {
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '可靠关注', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        return { liked: false, favorited: false, followed: false, selfAuthor: false }
      },
      async setFollow(postId, active) {
        expect(postId).toBe('42')
        expect(active).toBe(true)
        return { followed: true }
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    await wrapper.get('button[aria-label="关注作者"]').trigger('click')
    await flushPromises()

    const follow = wrapper.get('button[aria-label="关注作者"]')
    expect(follow.attributes('aria-pressed')).toBe('true')
    expect(follow.text()).toBe('已关注')
  })

  it('查看自己的帖子时不提供自我关注操作', async () => {
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '自己的帖子', description: '' },
          author: { id: '100', name: '当前用户', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        return { liked: false, favorited: false, followed: false, selfAuthor: true }
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    expect(wrapper.find('button[aria-label="关注作者"]').exists()).toBe(false)
  })

  it('新增评论后立即展示服务端返回的正式评论', async () => {
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '正式评论', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        return {
          viewerId: '100',
          liked: false,
          favorited: false,
          followed: false,
          selfAuthor: false,
        }
      },
      async createComment(postId, content) {
        expect(postId).toBe('42')
        expect(content).toBe('服务端，请给我正式评论')
        return {
          id: '900',
          userId: '100',
          membername: '当前读者',
          avatar: '/uploads/current-reader.png',
          commentContent: '服务端，请给我正式评论',
          commentTime: '2026-08-26T09:00:00Z',
        }
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    await wrapper.get('textarea[aria-label="添加评论内容"]')
      .setValue('服务端，请给我正式评论')
    await wrapper.get('button[aria-label="发布评论"]').trigger('click')
    await flushPromises()

    const comment = wrapper.get('li[data-comment-id="900"]')
    expect(comment.text()).toContain('当前读者')
    expect(comment.text()).toContain('服务端，请给我正式评论')
    expect(comment.get('time').attributes('datetime')).toBe('2026-08-26T09:00:00Z')
    expect(wrapper.get('textarea[aria-label="添加评论内容"]').element.value).toBe('')
  })

  it('只向评论作者提供编辑删除操作并采用服务端返回结果', async () => {
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '评论所有权', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [
            {
              id: '900',
              userId: '100',
              membername: '当前读者',
              avatar: '',
              commentContent: '我的原评论',
              commentTime: '2026-08-26T09:00:00Z',
            },
            {
              id: '901',
              userId: '200',
              membername: '其他读者',
              avatar: '',
              commentContent: '别人的评论',
              commentTime: '2026-08-26T09:01:00Z',
            },
          ],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        return {
          viewerId: '100',
          liked: false,
          favorited: false,
          followed: false,
          selfAuthor: false,
        }
      },
      async updateComment(id, content) {
        expect(id).toBe('900')
        expect(content).toBe('服务端确认的修改')
        return {
          id: '900',
          userId: '100',
          membername: '当前读者',
          avatar: '',
          commentContent: '服务端确认的修改',
          commentTime: '2026-08-26T09:10:00Z',
        }
      },
      async deleteComment(id) {
        expect(id).toBe('900')
        return '900'
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    expect(wrapper.find('button[aria-label="编辑评论 900"]').exists()).toBe(true)
    expect(wrapper.find('button[aria-label="删除评论 900"]').exists()).toBe(true)
    expect(wrapper.find('button[aria-label="编辑评论 901"]').exists()).toBe(false)
    expect(wrapper.find('button[aria-label="删除评论 901"]').exists()).toBe(false)

    await wrapper.get('button[aria-label="编辑评论 900"]').trigger('click')
    await wrapper.get('textarea[aria-label="编辑评论内容 900"]').setValue('服务端确认的修改')
    await wrapper.get('button[aria-label="保存评论 900"]').trigger('click')
    await flushPromises()

    const updated = wrapper.get('li[data-comment-id="900"]')
    expect(updated.text()).toContain('服务端确认的修改')
    expect(updated.get('time').attributes('datetime')).toBe('2026-08-26T09:10:00Z')

    await wrapper.get('button[aria-label="删除评论 900"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('li[data-comment-id="900"]').exists()).toBe(false)
    expect(wrapper.find('li[data-comment-id="901"]').exists()).toBe(true)
  })

  it('评论权限失败时保留正式评论并展示稳定反馈', async () => {
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '权限失败反馈', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [{
            id: '900',
            userId: '100',
            membername: '当前读者',
            avatar: '',
            commentContent: '服务端原评论',
            commentTime: '2026-08-26T09:00:00Z',
          }],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        return {
          viewerId: '100',
          liked: false,
          favorited: false,
          followed: false,
          selfAuthor: false,
        }
      },
      async updateComment() {
        throw new Error('forbidden')
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    await wrapper.get('button[aria-label="编辑评论 900"]').trigger('click')
    await wrapper.get('textarea[aria-label="编辑评论内容 900"]').setValue('不会本地生效')
    await wrapper.get('button[aria-label="保存评论 900"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('li[data-comment-id="900"]').text()).toContain('服务端原评论')
    expect(wrapper.get('[aria-label="评论操作反馈"]').text()).toContain('无权操作')
  })

  it('评论发布失败时不会制造缺少正式标识的本地评论', async () => {
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '发布失败不造假', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        return {
          viewerId: '100',
          liked: false,
          favorited: false,
          followed: false,
          selfAuthor: false,
        }
      },
      async createComment() {
        throw new Error('save failed')
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    await wrapper.get('textarea[aria-label="添加评论内容"]').setValue('不会成为本地评论')
    await wrapper.get('button[aria-label="发布评论"]').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('li[data-comment-id]').length).toBe(0)
    expect(wrapper.get('[aria-label="评论操作反馈"]').text()).toContain('发布失败')
  })

  it('登录用户的公共详情成功加载后由共享 module 记录浏览事实', async () => {
    const recordedPostIds = []
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '记录浏览事实', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        return {
          viewerId: '100',
          liked: false,
          favorited: false,
          followed: false,
          selfAuthor: false,
        }
      },
      async recordView(postId) {
        recordedPostIds.push(postId)
      },
    }
    mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    expect(recordedPostIds).toEqual(['42'])
  })

  it('作者头像和名称使用包含作者标识的同一命名路由导航', async () => {
    const navigations = []
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '稳定作者导航', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '/uploads/author.png' },
          images: [],
          comments: [],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        throw new Error('anonymous')
      },
    }
    const navigation = {
      openAuthor(authorId) {
        navigations.push({ name: 'author-detail', params: { authorId } })
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: {
        provide: {
          [postDetailHttpKey]: http,
          [postDetailNavigationKey]: navigation,
        },
      },
    })
    await flushPromises()

    const authorLink = wrapper.get('button[aria-label="查看作者 旅行者小林"]')
    expect(authorLink.get('img[alt="旅行者小林的头像"]').exists()).toBe(true)
    expect(authorLink.text()).toContain('旅行者小林')
    await authorLink.trigger('click')

    expect(navigations).toEqual([{ name: 'author-detail', params: { authorId: '7' } }])
  })

  it.each([
    ['匿名阅读', true, false],
    ['公共详情失败', false, true],
  ])('%s 不发送浏览记录写请求', async (_scenario, publicSucceeds, viewerSucceeds) => {
    let writes = 0
    const http = {
      async getPublicDetail() {
        if (!publicSucceeds) throw new Error('public detail failed')
        return {
          post: { id: '42', title: '不应记录', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        if (!viewerSucceeds) throw new Error('unauthorized')
        return {
          viewerId: '100',
          liked: false,
          favorited: false,
          followed: false,
          selfAuthor: false,
        }
      },
      async recordView() {
        writes += 1
      },
    }
    mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    expect(writes).toBe(0)
  })

  it.each([
    ['收藏', 'setFavorite', { active: true, count: 5 }],
    ['关注作者', 'setFollow', { followed: true }],
  ])('%s 写入期间禁用对应按钮且快速重复点击只发送一个请求', async (label, method, result) => {
    let resolveWrite
    let writes = 0
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '独立写入门禁', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        return { liked: false, favorited: false, followed: false, selfAuthor: false }
      },
      [method]() {
        writes += 1
        return new Promise((resolve) => { resolveWrite = resolve })
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    const button = wrapper.get(`button[aria-label="${label}"]`)
    await button.trigger('click')
    await button.trigger('click')

    expect(button.attributes('disabled')).toBeDefined()
    expect(writes).toBe(1)

    resolveWrite(result)
    await flushPromises()
    expect(wrapper.get(`button[aria-label="${label}"]`).attributes('disabled')).toBeUndefined()
  })

  it.each([
    ['点赞', 'setLike', '点赞失败', '12'],
    ['收藏', 'setFavorite', '收藏失败', '4'],
    ['关注作者', 'setFollow', '关注失败', '已关注'],
  ])('%s 写入失败时保留服务端读取状态且展示局部错误', async (label, method, errorText, visibleState) => {
    const http = {
      async getPublicDetail() {
        return {
          post: { id: '42', title: '失败不漂移', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 12, favorite: 4 },
        }
      },
      async getViewerState() {
        return { liked: true, favorited: true, followed: true, selfAuthor: false }
      },
      async [method]() {
        throw new Error('write failed')
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    await wrapper.get(`button[aria-label="${label}"]`).trigger('click')
    await flushPromises()

    const button = wrapper.get(`button[aria-label="${label}"]`)
    expect(button.attributes('aria-pressed')).toBe('true')
    expect(button.text()).toContain(visibleState)
    expect(wrapper.get('[role="status"]').text()).toContain(errorText)
  })

  it('公共详情失败时展示错误并允许重试', async () => {
    let attempts = 0
    const http = {
      async getPublicDetail() {
        attempts += 1
        if (attempts === 1) throw new Error('temporary failure')
        return {
          post: { id: '42', title: '重试后加载成功', description: '' },
          author: { id: '7', name: '旅行者小林', avatar: '' },
          images: [],
          comments: [],
          interactionCounts: { like: 0, favorite: 0 },
        }
      },
      async getViewerState() {
        return { liked: false, favorited: false, followed: false, selfAuthor: false }
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('帖子详情加载失败')

    await wrapper.get('button[aria-label="重试帖子详情"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="dialog"]').text()).toContain('重试后加载成功')
    expect(attempts).toBe(2)
  })

  it('切换帖子后忽略上一个帖子的迟到响应', async () => {
    const publicRequests = new Map()
    const detailFor = (postId, title) => ({
      post: { id: postId, title, description: '' },
      author: { id: '7', name: '旅行者小林', avatar: '' },
      images: [],
      comments: [],
      interactionCounts: { like: 0, favorite: 0 },
    })
    const http = {
      getPublicDetail(postId) {
        return new Promise((resolve) => publicRequests.set(postId, resolve))
      },
      async getViewerState() {
        return { liked: false, favorited: false, followed: false, selfAuthor: false }
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '1' },
      global: { provide: { [postDetailHttpKey]: http } },
    })

    await wrapper.setProps({ postId: '2' })
    publicRequests.get('2')(detailFor('2', '第二个帖子'))
    await flushPromises()
    expect(wrapper.get('[role="dialog"]').text()).toContain('第二个帖子')

    publicRequests.get('1')(detailFor('1', '第一个帖子的迟到响应'))
    await flushPromises()
    expect(wrapper.get('[role="dialog"]').text()).toContain('第二个帖子')
    expect(wrapper.get('[role="dialog"]').text()).not.toContain('第一个帖子的迟到响应')
  })

  it('关闭并重新打开时不显示上一次的帖子状态', async () => {
    let attempts = 0
    let resolveReopen
    const detailFor = (title) => ({
      post: { id: '42', title, description: '' },
      author: { id: '7', name: '旅行者小林', avatar: '' },
      images: [],
      comments: [],
      interactionCounts: { like: 0, favorite: 0 },
    })
    const http = {
      getPublicDetail() {
        attempts += 1
        if (attempts === 1) return Promise.resolve(detailFor('上一次打开的帖子'))
        return new Promise((resolve) => { resolveReopen = resolve })
      },
      async getViewerState() {
        return { liked: false, favorited: false, followed: false, selfAuthor: false }
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })
    await flushPromises()
    expect(wrapper.get('[role="dialog"]').text()).toContain('上一次打开的帖子')

    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })

    expect(wrapper.get('[role="dialog"]').text()).toContain('加载中…')
    expect(wrapper.get('[role="dialog"]').text()).not.toContain('上一次打开的帖子')

    resolveReopen(detailFor('重新加载后的帖子'))
    await flushPromises()
    expect(wrapper.get('[role="dialog"]').text()).toContain('重新加载后的帖子')
  })

  it('关闭详情后忽略尚未返回的响应且不记录浏览事实', async () => {
    let resolvePublicDetail
    let resolveViewerState
    let viewWrites = 0
    const http = {
      getPublicDetail() {
        return new Promise((resolve) => { resolvePublicDetail = resolve })
      },
      getViewerState() {
        return new Promise((resolve) => { resolveViewerState = resolve })
      },
      async recordView() {
        viewWrites += 1
      },
    }
    const wrapper = mount(PostDetail, {
      props: { open: true, postId: '42' },
      global: { provide: { [postDetailHttpKey]: http } },
    })

    await wrapper.setProps({ open: false })
    resolvePublicDetail({
      post: { id: '42', title: '关闭后的迟到响应', description: '' },
      author: { id: '7', name: '旅行者小林', avatar: '' },
      images: [],
      comments: [],
      interactionCounts: { like: 0, favorite: 0 },
    })
    resolveViewerState({
      viewerId: '100',
      liked: false,
      favorited: false,
      followed: false,
      selfAuthor: false,
    })
    await flushPromises()

    expect(viewWrites).toBe(0)
    await wrapper.setProps({ open: true })
    expect(wrapper.get('[role="dialog"]').text()).toContain('加载中…')
    expect(wrapper.get('[role="dialog"]').text()).not.toContain('关闭后的迟到响应')
  })
})

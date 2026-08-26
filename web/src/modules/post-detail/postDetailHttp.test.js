import { describe, expect, it } from 'vitest'

import { createPostDetailHttp } from './postDetailHttp.js'

describe('帖子详情 HTTP adapter', () => {
  it('按 postId 读取公共详情并规范化服务端资源地址', async () => {
    const http = {
      async get(url, config) {
        if (url !== '/lyw/web/post/detail') throw new Error(`unexpected url: ${url}`)
        if (config.params.postId !== '42') throw new Error('unexpected postId')
        return {
          data: {
            success: true,
            content: {
              post: { id: '42', title: '桂林山水', description: '', images: [] },
              author: { id: '7', name: '旅行者小林', avatar: '/uploads/avatar.png' },
              images: ['/uploads/one.jpg', 'https://cdn.example.com/two.jpg'],
              comments: [
                { id: '501', avatar: '/uploads/comment-avatar.png', commentContent: '真漂亮' },
              ],
              interactionCounts: { like: 1, favorite: 2 },
            },
          },
        }
      },
    }

    const detail = await createPostDetailHttp(http).getPublicDetail('42')

    expect(detail.author.avatar).toBe('/lyw/uploads/avatar.png')
    expect(detail.images).toEqual([
      '/lyw/uploads/one.jpg',
      'https://cdn.example.com/two.jpg',
    ])
    expect(detail.comments[0].avatar).toBe('/lyw/uploads/comment-avatar.png')
  })

  it('通过帖子详情正式接口提交点赞、收藏和作者关注状态', async () => {
    const calls = []
    const http = {
      async post(url, body) {
        calls.push({ url, body })
        if (url.endsWith('/follow')) {
          return { data: { success: true, content: { followed: body.active } } }
        }
        return { data: { success: true, content: { active: body.active, count: 21 } } }
      },
    }
    const adapter = createPostDetailHttp(http)

    await expect(adapter.setLike('42', true)).resolves.toEqual({ active: true, count: 21 })
    await expect(adapter.setFavorite('42', false)).resolves.toEqual({ active: false, count: 21 })
    await expect(adapter.setFollow('42', true)).resolves.toEqual({ followed: true })

    expect(calls).toEqual([
      { url: '/lyw/web/post/detail/like', body: { postId: '42', active: true } },
      { url: '/lyw/web/post/detail/favorite', body: { postId: '42', active: false } },
      { url: '/lyw/web/post/detail/follow', body: { postId: '42', active: true } },
    ])
  })

  it('通过评论正式接口新增、修改和删除评论', async () => {
    const calls = []
    const http = {
      async post(url, body) {
        calls.push({ url, body })
        if (url.endsWith('/del-comment')) {
          return { data: { success: true, content: '900' } }
        }
        return {
          data: {
            success: true,
            content: {
              id: '900',
              userId: '100',
              membername: '当前读者',
              avatar: '/uploads/commenter.png',
              commentContent: body.content,
              commentTime: '2026-08-26T09:00:00Z',
            },
          },
        }
      },
    }
    const adapter = createPostDetailHttp(http)

    const created = await adapter.createComment('42', '新评论')
    const updated = await adapter.updateComment('900', '修改后的评论')
    const deletedId = await adapter.deleteComment('900')

    expect(created.avatar).toBe('/lyw/uploads/commenter.png')
    expect(updated.commentContent).toBe('修改后的评论')
    expect(deletedId).toBe('900')
    expect(calls).toEqual([
      { url: '/lyw/web/comment/save-comment', body: { postId: '42', content: '新评论' } },
      { url: '/lyw/web/comment/update-comment', body: { id: '900', content: '修改后的评论' } },
      { url: '/lyw/web/comment/del-comment', body: { id: '900' } },
    ])
  })

  it('通过浏览记录正式接口记录成功打开的帖子', async () => {
    const http = {
      async post(url, body) {
        expect(url).toBe('/lyw/web/postview/save')
        expect(body).toEqual({ postId: '42' })
        return { data: { success: true, content: null } }
      },
    }

    await expect(createPostDetailHttp(http).recordView('42')).resolves.toBeNull()
  })
})

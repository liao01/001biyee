import { describe, expect, it } from 'vitest'

import { toPostPreview } from './postPreviewAdapter.js'

describe('帖子预览适配器', () => {
  it('把后端帖子响应收敛为统一预览模型', () => {
    const preview = toPostPreview({
      postId: '42',
      postTitle: '山海之间',
      postContent: '一段足够长的旅行正文',
      postMembername: '旅行者小林',
      imageUrls: '/first.jpg,/second.jpg',
      postTime: '2026-08-20',
      categoryCode: 'NATURAL_SCENERY',
      categoryName: '自然风光',
    }, { baseUrl: 'http://localhost/lyw', maxDescriptionLength: 6 })

    expect(preview).toEqual({
      id: '42',
      raw: expect.objectContaining({ postId: '42' }),
      image: 'http://localhost/lyw/first.jpg',
      title: '山海之间',
      description: '一段足够长的...',
      author: '旅行者小林',
      categoryCode: 'NATURAL_SCENERY',
      categoryName: '自然风光',
      publishedAt: '2026-08-20',
    })
  })
})

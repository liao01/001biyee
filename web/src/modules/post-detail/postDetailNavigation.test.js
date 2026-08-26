import { describe, expect, it } from 'vitest'

import { createPostDetailNavigation } from './postDetailNavigation.js'

describe('帖子详情作者导航 adapter', () => {
  it('使用包含作者标识的命名路由', async () => {
    const destinations = []
    const router = {
      push(destination) {
        destinations.push(destination)
        return Promise.resolve()
      },
    }

    await createPostDetailNavigation(router).openAuthor(7)

    expect(destinations).toEqual([{
      name: 'author-detail',
      params: { authorId: '7' },
    }])
  })
})

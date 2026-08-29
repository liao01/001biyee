import { describe, expect, it, vi } from 'vitest'

import { buildDiscoveryParams, fetchPostCategories } from './postCategories.js'

describe('帖子分类 API 边界', () => {
  it('只返回后端成功响应中的正式分类', async () => {
    const http = {
      get: vi.fn().mockResolvedValue({
        data: {
          success: true,
          content: [
            { code: 'CITY_WALK', name: '城市漫游' },
            { code: 'FOOD', name: '美食' },
          ],
        },
      }),
    }

    await expect(fetchPostCategories(http, '/lyw')).resolves.toEqual([
      { code: 'CITY_WALK', name: '城市漫游' },
      { code: 'FOOD', name: '美食' },
    ])
    expect(http.get).toHaveBeenCalledWith('/lyw/web/post/categories')
  })

  it('把发现视图和分类收敛为后端查询参数', () => {
    expect(buildDiscoveryParams({ view: 'LATEST' })).toEqual({ view: 'LATEST' })
    expect(buildDiscoveryParams({ categoryCode: 'FOOD' })).toEqual({ categoryCode: 'FOOD' })
    expect(buildDiscoveryParams({ view: 'RECOMMENDED' })).toEqual({})
  })
})

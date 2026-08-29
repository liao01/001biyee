import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  keyword: '',
}))

vi.mock('axios', () => ({ default: { get: mocks.get } }))
vi.mock('../../store/search.js', () => ({
  useSearchStore: () => ({ keyword: mocks.keyword }),
}))
vi.mock('../../utils/baseUrl', () => ({ BASE_URL: 'http://example.test' }))

import CardList from './cardlist.vue'

const findButton = (wrapper, text) => wrapper.findAll('button').find((button) => button.text() === text)

describe('发现页分类视图', () => {
  beforeEach(() => {
    mocks.get.mockImplementation((url) => {
      if (url.endsWith('/web/post/categories')) {
        return Promise.resolve({
          data: {
            success: true,
            content: [
              { code: 'CITY_WALK', name: '城市漫游' },
              { code: 'NATURAL_SCENERY', name: '自然风光' },
              { code: 'FOOD', name: '美食' },
            ],
          },
        })
      }
      return Promise.resolve({ data: { success: true, content: [] } })
    })
  })

  it('把推荐最新和后端分类分开，并按选择请求后端', async () => {
    const wrapper = mount(CardList, {
      global: {
        stubs: {
          PostDetail: true,
          PostPreview: true,
          CompassOutlined: true,
        },
      },
    })
    await flushPromises()

    expect(findButton(wrapper, '推荐').exists()).toBe(true)
    expect(findButton(wrapper, '最新').exists()).toBe(true)
    expect(findButton(wrapper, '美食').exists()).toBe(true)

    await findButton(wrapper, '最新').trigger('click')
    await flushPromises()
    expect(mocks.get).toHaveBeenLastCalledWith(
      'http://example.test/lyw/web/post/post-findAll',
      { params: { view: 'LATEST' } },
    )

    await findButton(wrapper, '美食').trigger('click')
    await flushPromises()
    expect(mocks.get).toHaveBeenLastCalledWith(
      'http://example.test/lyw/web/post/post-findAll',
      { params: { categoryCode: 'FOOD' } },
    )
  })
})

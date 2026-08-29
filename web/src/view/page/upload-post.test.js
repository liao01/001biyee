import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('axios', () => ({ default: { get: mocks.get, post: mocks.post } }))
vi.mock('../../utils/baseUrl', () => ({ BASE_URL: 'http://example.test' }))

import UploadPost from './upload-post.vue'

describe('发布页布局', () => {
  it('采用写作与发布设置双栏，并只保留一个发布动作', async () => {
    mocks.get.mockResolvedValue({
      data: {
        success: true,
        content: [
          { code: 'CITY_WALK', name: '城市漫游' },
          { code: 'FOOD', name: '美食' },
        ],
      },
    })
    const wrapper = mount(UploadPost, {
      global: {
        stubs: {
          'a-input': true,
          'a-textarea': true,
          'a-upload': { template: '<div><slot /></div>' },
          'a-modal': { template: '<div><slot /></div>' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.find('.publish-editor__main').exists()).toBe(true)
    expect(wrapper.find('.publish-editor__settings').exists()).toBe(true)
    expect(mocks.get).toHaveBeenCalledWith('http://example.test/lyw/web/post/categories')
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('城市漫游')
      expect(wrapper.text()).toContain('美食')
    })
    expect(wrapper.findAll('button').filter(button => button.text() === '发布旅行')).toHaveLength(1)
  })
})

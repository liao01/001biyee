import { flushPromises, mount } from '@vue/test-utils'
import { h } from 'vue'
import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn().mockResolvedValue({
    data: {
      success: true,
      content: {
        page: [{ postId: '42', postTitle: '桂林山水之旅', categoryName: '自然风光' }],
        total: 1,
      },
    },
  }),
}))

vi.mock('axios', () => ({ default: { get: mocks.get, post: vi.fn() } }))
vi.mock('../../store/index.js', () => ({ default: { state: { member: {} } } }))
vi.mock('../../utils/baseUrl', () => ({ BASE_URL: 'http://example.test' }))

import PostHistory from './PostHistory.vue'

const TableStub = {
  props: ['dataSource', 'rowKey'],
  setup(props, { slots }) {
    return () => h('div', { 'data-row-key': props.rowKey }, (props.dataSource || []).map(record =>
      h('div', { class: 'row' }, slots.bodyCell?.({ column: { dataIndex: 'postTitle' }, record })),
    ))
  },
}

describe('发布历史', () => {
  it('使用 postId 作为稳定行键并从标题打开统一详情', async () => {
    mocks.get.mockResolvedValue({
      data: {
        success: true,
        content: {
          page: [{ postId: '42', postTitle: '桂林山水之旅', categoryName: '自然风光' }],
          total: 1,
        },
      },
    })
    const wrapper = mount(PostHistory, {
      global: {
        stubs: {
          ContentListPage: { template: '<div><slot /></div>' },
          PostDetail: { props: ['open', 'postId'], template: '<div data-testid="detail" :data-open="open" :data-post-id="postId" />' },
          'a-table': TableStub,
          'a-space': { template: '<div><slot /></div>' },
          'a-popconfirm': { template: '<div><slot /></div>' },
          'a-button': { template: '<button><slot /></button>' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.get('[data-row-key]').attributes('data-row-key')).toBe('postId')
    await wrapper.get('button[aria-label="查看帖子 桂林山水之旅"]').trigger('click')
    expect(wrapper.get('[data-testid="detail"]').attributes('data-open')).toBe('true')
    expect(wrapper.get('[data-testid="detail"]').attributes('data-post-id')).toBe('42')
  })
})

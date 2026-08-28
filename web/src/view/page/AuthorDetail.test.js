import { flushPromises, mount } from '@vue/test-utils'
import { h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import AuthorDetail from './AuthorDetail.vue'

const route = { params: { authorId: '7' } }

vi.mock('vue-router', () => ({
  useRoute: () => route,
}))

vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

import axios from 'axios'

const WaterfallStub = {
  props: ['list'],
  setup(props, { slots }) {
    return () => h('div', props.list.map((item) => slots.item?.({ item })))
  },
}

const pageStubs = {
  Waterfall: WaterfallStub,
  PostDetail: {
    props: ['open', 'postId'],
    emits: ['update:open'],
    template: '<div data-testid="shared-post-detail" :data-open="String(open)" :data-post-id="String(postId)" />',
  },
  'a-card': {
    emits: ['click'],
    template: '<button type="button" class="post-preview" @click="$emit(\'click\')"><slot name="cover" /><slot /></button>',
  },
  'a-card-meta': true,
  'a-avatar': true,
  'a-button': true,
  'a-descriptions': true,
  'a-descriptions-item': true,
}

describe('作者详情路由', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    axios.get.mockImplementation(async (url) => {
      if (url.includes('/findAllUser')) {
        return { data: { success: true, content: [{ name: '旅行者小林' }] } }
      }
      if (url.includes('/User-Like-Count')) {
        return { data: { success: true, content: 12 } }
      }
      if (url.includes('/byUserIds')) {
        return { data: { success: true, content: 3 } }
      }
      return { data: { success: true, content: {} } }
    })
    axios.post.mockResolvedValue({
      data: {
        success: true,
        content: [{
          postId: '42',
          postTitle: '作者的帖子',
          postContent: '作者页保持自己的列表加载。',
          imageUrls: '/uploads/cover.jpg',
          postMembername: '旅行者小林',
          postTime: '2026-08-26T09:00:00Z',
        }],
      },
    })
  })

  it('刷新页面时直接使用路由 authorId 加载正确作者', async () => {
    mount(AuthorDetail, {
      global: {
        stubs: pageStubs,
      },
    })
    await flushPromises()

    const profileCall = axios.get.mock.calls.find(([url]) => url.includes('/findAllUser'))
    const likeCall = axios.get.mock.calls.find(([url]) => url.includes('/User-Like-Count'))
    const followerCall = axios.get.mock.calls.find(([url]) => url.includes('/byUserIds'))
    expect(profileCall[1].params.userId).toBe('7')
    expect(likeCall[1].params.userId).toBe('7')
    expect(followerCall[1].params.userId).toBe('7')
    expect(axios.post).toHaveBeenCalledWith(
      expect.stringContaining('/post-UserPostQuery'),
      { userid: '7' },
    )
  })

  it('作者帖子点击后只用 postId 打开共享详情', async () => {
    const wrapper = mount(AuthorDetail, {
      global: { stubs: pageStubs },
    })
    await flushPromises()

    await wrapper.get('.post-preview').trigger('click')

    const detail = wrapper.get('[data-testid="shared-post-detail"]')
    expect(detail.attributes('data-open')).toBe('true')
    expect(detail.attributes('data-post-id')).toBe('42')
  })
})

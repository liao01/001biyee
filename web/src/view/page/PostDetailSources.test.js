import { flushPromises, mount } from '@vue/test-utils'
import { h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import CardList from './cardlist.vue'
import CardListView from './cardlistView.vue'
import FavoriteList from './FavoriteList.vue'

vi.mock('../../store/search.js', () => ({
  useSearchStore: () => ({ keyword: '' }),
}))

vi.mock('axios', () => ({
  default: {
    get: vi.fn(),
  },
}))

import axios from 'axios'

const WaterfallStub = {
  props: ['list'],
  setup(props, { slots }) {
    return () => h('div', props.list.map((item) => slots.item?.({ item })))
  },
}

const CardStub = {
  emits: ['click'],
  template: '<button type="button" class="custom-card" @click="$emit(\'click\')"><slot name="cover" /><slot /></button>',
}

const PostDetailStub = {
  props: ['open', 'postId'],
  emits: ['update:open'],
  template: '<div data-testid="shared-post-detail" :data-open="String(open)" :data-post-id="String(postId)" />',
}

const sourceCases = [
  ['全部帖子', CardList, '/web/post/post-findAll'],
  ['浏览历史', CardListView, '/web/postview/find'],
  ['收藏帖子', FavoriteList, '/web/userAction/favorite'],
]

describe('帖子来源接入共享详情', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    axios.get.mockResolvedValue({
      data: {
        success: true,
        content: [{
          postId: '42',
          postTitle: '只传 postId',
          postContent: '来源页面保留自己的列表数据映射。',
          imageUrls: '/uploads/cover.jpg',
          membername: '旅行者小林',
          postTime: '2026-08-26T09:00:00Z',
        }],
      },
    })
  })

  it.each(sourceCases)('%s 点击帖子后只用 postId 打开共享详情', async (_name, SourcePage, endpoint) => {
    const wrapper = mount(SourcePage, {
      global: {
        stubs: {
          Waterfall: WaterfallStub,
          PostDetail: PostDetailStub,
          'a-card': CardStub,
          'a-card-meta': true,
        },
      },
    })
    await flushPromises()

    expect(axios.get.mock.calls.some(([url]) => url.includes(endpoint))).toBe(true)
    await wrapper.get('.custom-card').trigger('click')

    const detail = wrapper.get('[data-testid="shared-post-detail"]')
    expect(detail.attributes('data-open')).toBe('true')
    expect(detail.attributes('data-post-id')).toBe('42')
  })
})

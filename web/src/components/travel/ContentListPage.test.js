import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ContentListPage from './ContentListPage.vue'

const PostPreviewStub = {
  props: ['post'],
  emits: ['open'],
  template: '<button data-testid="post-preview" @click="$emit(\'open\', post)">{{ post.title }}</button>',
}

const PostDetailStub = {
  props: ['open', 'postId'],
  template: '<div data-testid="post-detail">{{ open }}:{{ postId }}</div>',
}

describe('内容列表页骨架', () => {
  it('统一展示帖子预览并只用帖子标识打开共享详情', async () => {
    const wrapper = mount(ContentListPage, {
      props: {
        title: '我的收藏',
        subtitle: '收藏的旅行内容',
        loading: false,
        emptyText: '暂无收藏',
        posts: [{ id: '42', title: '山海之间' }],
      },
      global: {
        stubs: { PostPreview: PostPreviewStub, PostDetail: PostDetailStub },
      },
    })

    expect(wrapper.text()).toContain('我的收藏')
    await wrapper.get('[data-testid="post-preview"]').trigger('click')

    expect(wrapper.get('[data-testid="post-detail"]').text()).toBe('true:42')
  })

  it('为加载和空列表提供统一状态', async () => {
    const wrapper = mount(ContentListPage, {
      props: {
        title: '浏览历史',
        loading: true,
        loadingText: '正在整理浏览记录…',
        emptyText: '暂无浏览记录',
        posts: [],
      },
      global: {
        stubs: { PostPreview: PostPreviewStub, PostDetail: PostDetailStub },
      },
    })

    expect(wrapper.text()).toContain('正在整理浏览记录…')
    await wrapper.setProps({ loading: false })
    expect(wrapper.text()).toContain('暂无浏览记录')
  })

  it('允许发布历史复用页面状态并提供专用内容', () => {
    const wrapper = mount(ContentListPage, {
      props: {
        title: '发布历史',
        loading: false,
        emptyText: '暂无发布内容',
        hasCustomContent: true,
      },
      slots: { default: '<div data-testid="history-table">发布记录表格</div>' },
      global: {
        stubs: { PostPreview: PostPreviewStub, PostDetail: PostDetailStub },
      },
    })

    expect(wrapper.get('[data-testid="history-table"]').text()).toBe('发布记录表格')
  })
})

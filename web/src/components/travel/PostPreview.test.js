import { mount } from '@vue/test-utils'
import { describe, expect, test } from 'vitest'

import PostPreview from './PostPreview.vue'

describe('旅行帖子预览', () => {
  test('展示核心元信息并通过 open 事件打开帖子', async () => {
    const post = {
      id: 'post-1',
      title: '在大理等一场风',
      description: '洱海边的日子总是慢的。',
      image: '/dali.jpg',
      author: '小满在路上',
      location: '云南·大理',
      publishedAt: '2026-08-27',
    }
    const wrapper = mount(PostPreview, { props: { post } })

    expect(wrapper.text()).toContain(post.title)
    expect(wrapper.text()).toContain(post.author)
    expect(wrapper.text()).toContain(post.location)

    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('open')).toEqual([[post]])
  })
})

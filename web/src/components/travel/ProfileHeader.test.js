import { mount } from '@vue/test-utils'
import { describe, expect, test } from 'vitest'

import ProfileHeader from './ProfileHeader.vue'

describe('旅行者身份区', () => {
  test('展示身份、统计与自己的操作入口', async () => {
    const profile = {
      name: '小满在路上',
      bio: '把走过的路，写成可以再次抵达的故事',
      location: '广州',
      avatar: '/avatar.jpg',
      stats: [
        { label: '关注', value: 186 },
        { label: '粉丝', value: 2346 },
      ],
    }
    const wrapper = mount(ProfileHeader, { props: { profile, isSelf: true } })

    expect(wrapper.text()).toContain(profile.name)
    expect(wrapper.text()).toContain(profile.bio)
    expect(wrapper.text()).toContain('2,346')

    await wrapper.get('button[aria-label="编辑个人资料"]').trigger('click')
    expect(wrapper.emitted('edit')).toHaveLength(1)
  })
})

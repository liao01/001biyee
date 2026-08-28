import { mount } from '@vue/test-utils'
import { describe, expect, test, vi } from 'vitest'

vi.mock('./the-header.vue', () => ({
  default: { name: 'TheHeader', template: '<header data-testid="travel-header" />' },
}))
vi.mock('./the-sider.vue', () => ({
  default: { name: 'TheSider', template: '<nav data-testid="travel-sidebar" />' },
}))

import AppShell from './AppShell.vue'

const passthrough = { template: '<div><slot /></div>' }

describe('用户端应用外壳', () => {
  test('提供统一桌面布局与移动导航容器', () => {
    const wrapper = mount(AppShell, {
      global: {
        stubs: {
          'a-layout': passthrough,
          'a-layout-header': passthrough,
          'a-layout-sider': passthrough,
          'a-layout-content': passthrough,
          'router-view': { template: '<main data-testid="route-content" />' },
          TheHeader: { template: '<header data-testid="travel-header" />' },
          TheSider: { template: '<nav data-testid="travel-sidebar" />' },
        },
      },
    })

    expect(wrapper.find('.travel-shell').exists()).toBe(true)
    expect(wrapper.find('.travel-shell__desktop-sidebar').exists()).toBe(true)
    expect(wrapper.find('.travel-shell__content').exists()).toBe(true)
    expect(wrapper.find('.travel-shell__mobile-nav').exists()).toBe(true)
  })
})

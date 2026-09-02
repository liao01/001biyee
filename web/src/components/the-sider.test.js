import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createStore } from 'vuex'
import { describe, expect, it } from 'vitest'

import TheSider from './the-sider.vue'

const mountSider = async (mobile = false) => {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/CardList', component: { template: '<div />' } },
    { path: '/itineraries', component: { template: '<div />' } },
    { path: '/itineraries/:id', component: { template: '<div />' } },
  ] })
  const store = createStore({ state: { member: { id: '42' } } })
  await router.push('/itineraries/10')
  await router.isReady()
  const wrapper = mount(TheSider, {
    props: { mobile },
    global: { plugins: [router, store], stubs: { TheLogin: true } },
  })
  return wrapper
}

describe('用户导航中的行程入口', () => {
  it('桌面侧栏显示正式入口并在编辑深链保持选中', async () => {
    const wrapper = await mountSider()
    const item = wrapper.findAll('.travel-sider__item').find((entry) => entry.text().includes('我的行程'))
    expect(item).toBeTruthy()
    expect(item.classes()).toContain('is-active')
  })

  it('移动底栏维持五个顶层入口，不挤入第六项', async () => {
    const wrapper = await mountSider(true)
    expect(wrapper.findAll('.travel-sider__item')).toHaveLength(5)
    expect(wrapper.text()).not.toContain('我的行程')
  })
})

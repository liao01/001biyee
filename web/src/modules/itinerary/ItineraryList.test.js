import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ItineraryList from './ItineraryList.vue'
import { itineraryHttpKey } from './itineraryHttp.js'

describe('行程列表页', () => {
  it('展示标题、日期、主要目的地、状态和更新时间，并提供创建与编辑深链', async () => {
    const api = { list: async () => ({ items: [{
      id: '101', title: '杭州周末', startDate: '2026-09-01', endDate: '2026-09-02',
      primaryDestination: '杭州', status: 'DRAFT', version: 1,
      updatedAt: '2026-09-01T08:00:00Z',
    }], nextCursor: null }) }
    const wrapper = mount(ItineraryList, {
      global: {
        provide: { [itineraryHttpKey]: api },
        stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } },
      },
    })
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('我的行程')
    expect(wrapper.text()).toContain('杭州周末')
    expect(wrapper.text()).toContain('杭州')
    expect(wrapper.text()).toContain('草稿')
    expect(wrapper.text()).toContain('2026年9月1日—2026年9月2日')
    expect(wrapper.get('a[href="/itineraries/new"]').text()).toContain('创建行程')
    expect(wrapper.get('a[href="/itineraries/101"]').exists()).toBe(true)
  })

  it('请求失败时提供可操作的重试入口', async () => {
    let attempts = 0
    const api = { list: async () => { attempts++; throw new Error('网络暂不可用') } }
    const wrapper = mount(ItineraryList, {
      global: {
        provide: { [itineraryHttpKey]: api },
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    })
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('网络暂不可用')
    await wrapper.get('button[aria-label="重试加载行程"]').trigger('click')
    expect(attempts).toBe(2)
  })
})

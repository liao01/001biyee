import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'

import ItineraryCreate from './ItineraryCreate.vue'
import { itineraryHttpKey } from './itineraryHttp.js'

describe('创建行程页', () => {
  it('校验并提交完整创建命令，成功后进入编辑器', async () => {
    const api = { create: vi.fn().mockResolvedValue({ itineraryId: '501', version: 1 }) }
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: '/itineraries/new', component: ItineraryCreate },
      { path: '/itineraries/:itineraryId', component: { template: '<div />' } },
      { path: '/itineraries', component: { template: '<div />' } },
    ] })
    await router.push('/itineraries/new')
    await router.isReady()
    const wrapper = mount(ItineraryCreate, {
      global: { plugins: [router], provide: { [itineraryHttpKey]: api } },
    })

    await wrapper.get('#itinerary-title').setValue('杭州周末')
    await wrapper.get('#itinerary-start-date').setValue('2026-09-01')
    await wrapper.get('#itinerary-end-date').setValue('2026-09-02')
    await wrapper.get('#itinerary-time-zone').setValue('Asia/Tokyo')
    await wrapper.get('#destination-name-0').setValue('杭州')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(api.create).toHaveBeenCalledTimes(1)
    const command = api.create.mock.calls[0][0]
    expect(command.expectedVersion).toBe(0)
    expect(command.commandId).toMatch(/^[0-9a-f-]{36}$/)
    expect(command.payload).toMatchObject({
      title: '杭州周末', timeZone: 'Asia/Tokyo', baseCurrency: 'CNY',
      destinations: [{ name: '杭州', countryCode: 'CN', timeZone: 'Asia/Tokyo' }],
    })
    expect(router.currentRoute.value.path).toBe('/itineraries/501')
  })

  it('缺少必填项时显示字段错误并聚焦错误摘要', async () => {
    const api = { create: vi.fn() }
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: '/itineraries/new', component: ItineraryCreate },
      { path: '/itineraries', component: { template: '<div />' } },
    ] })
    await router.push('/itineraries/new')
    await router.isReady()
    const wrapper = mount(ItineraryCreate, {
      global: {
        plugins: [router],
        provide: { [itineraryHttpKey]: api },
      },
    })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('请完善以下信息')
    expect(wrapper.get('#itinerary-title-error').text()).toContain('标题')
    expect(api.create).not.toHaveBeenCalled()
  })
})

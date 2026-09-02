import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'

import ItineraryEditor from './ItineraryEditor.vue'
import { itineraryHttpKey } from './itineraryHttp.js'

const detail = () => ({
  id: '10', title: '杭州周末', startDate: '2026-09-01', endDate: '2026-09-02',
  timeZone: 'Asia/Shanghai', baseCurrency: 'CNY', status: 'DRAFT', version: 1,
  suggestedStatus: 'PLANNED', allowedTransitions: ['PLANNED', 'CANCELLED'],
  destinations: [{ id: '20', name: '杭州', countryCode: 'CN', timeZone: 'Asia/Shanghai' }],
  days: [{ id: '30', date: '2026-09-01', items: [
    { id: '40', dayId: '30', title: '西湖散步', startTime: '09:00', endTime: '10:00', position: 1024 },
    { id: '41', dayId: '30', title: '午餐', startTime: null, endTime: null, position: 2048 },
  ] }, { id: '31', date: '2026-09-02', items: [] }],
})

const mountEditor = async (api) => {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/itineraries/:itineraryId', component: ItineraryEditor },
    { path: '/itineraries', component: { template: '<div />' } },
  ] })
  await router.push('/itineraries/10')
  await router.isReady()
  const wrapper = mount(ItineraryEditor, {
    attachTo: document.body,
    global: { plugins: [router], provide: { [itineraryHttpKey]: api } },
  })
  await flushPromises()
  return wrapper
}

describe('行程编辑器页面', () => {
  it('按日期展示条目，键盘按钮可排序且焦点不丢失', async () => {
    const api = {
      get: vi.fn().mockResolvedValue(detail()),
      reorderItems: vi.fn().mockResolvedValue({ version: 2 }),
    }
    const wrapper = await mountEditor(api)
    const moveDown = wrapper.get('button[aria-label="下移 西湖散步"]')
    moveDown.element.focus()
    await moveDown.trigger('click')
    await flushPromises()

    expect(api.reorderItems.mock.calls[0][2]).toMatchObject({
      expectedVersion: 1, payload: { itemIds: ['41', '40'] },
    })
    expect(wrapper.get('[role="status"]').text()).toContain('已保存')
    expect(document.activeElement.getAttribute('aria-label')).toBe('上移 西湖散步')
    expect(wrapper.text()).toContain('未定时间')
  })

  it('提供基本信息、目的地、条目和状态操作，并在删除前确认', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const api = {
      get: vi.fn().mockResolvedValue(detail()),
      updateOverview: vi.fn().mockResolvedValue({ version: 2 }),
      replaceDestinations: vi.fn().mockResolvedValue({ version: 3 }),
      addItem: vi.fn().mockResolvedValue({ version: 4, itemId: '99' }),
      deleteItem: vi.fn().mockResolvedValue({ version: 5 }),
      transition: vi.fn().mockResolvedValue({ version: 6 }),
    }
    const wrapper = await mountEditor(api)
    expect(wrapper.get('form[aria-label="基本信息"]')).toBeTruthy()
    expect(wrapper.get('form[aria-label="目的地"]')).toBeTruthy()
    await wrapper.findAll('.itinerary-day__header button')[0].trigger('click')
    expect(wrapper.get('form[aria-label="安排项"]')).toBeTruthy()
    await wrapper.get('button[aria-label="删除 午餐"]').trigger('click')
    await flushPromises()
    expect(window.confirm).toHaveBeenCalled()
    expect(api.deleteItem).toHaveBeenCalledWith('10', '41', expect.any(Object))
    expect(wrapper.get('button[aria-label="将状态改为 PLANNED"]')).toBeTruthy()
  })

  it('版本冲突显示重载入口，不把失败显示成已保存', async () => {
    const conflict = Object.assign(new Error('行程已被更新，请重新加载'), {
      status: 409, errorCode: 'VERSION_CONFLICT',
    })
    const api = {
      get: vi.fn().mockResolvedValue(detail()),
      reorderItems: vi.fn().mockRejectedValue(conflict),
    }
    const wrapper = await mountEditor(api)
    await wrapper.get('button[aria-label="下移 西湖散步"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('重新加载')
    expect(wrapper.get('button[aria-label="重新加载行程"]')).toBeTruthy()
    expect(wrapper.get('[role="status"]').text()).not.toContain('已保存')
  })
})

import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'

import ItineraryEditor from './ItineraryEditor.vue'
import { itineraryHttpKey } from './itineraryHttp.js'
import { itineraryPlanningHttpKey } from '../itinerary-planning/itineraryPlanningHttp.js'
import { itineraryMapHttpKey } from '../itinerary-map/itineraryMapHttp.js'

const deferred = () => {
  let resolve
  const promise = new Promise((yes) => { resolve = yes })
  return { promise, resolve }
}

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

const mountEditor = async (api, planningApi = null, mapApi = null) => {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/itineraries/:itineraryId', component: ItineraryEditor },
    { path: '/itineraries', component: { template: '<div />' } },
  ] })
  await router.push('/itineraries/10')
  await router.isReady()
  const wrapper = mount(ItineraryEditor, {
    attachTo: document.body,
    global: { plugins: [router], provide: {
      [itineraryHttpKey]: api,
      ...(planningApi ? { [itineraryPlanningHttpKey]: planningApi } : {}),
      ...(mapApi ? { [itineraryMapHttpKey]: mapApi } : {}),
    } },
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

  it('写命令在途和投影重读期间禁用其他写入口，避免取消已排队命令', async () => {
    const pending = deferred()
    const api = {
      get: vi.fn().mockResolvedValue(detail()),
      updateOverview: vi.fn().mockReturnValue(pending.promise),
    }
    const wrapper = await mountEditor(api)

    await wrapper.get('form[aria-label="基本信息"]').trigger('submit')
    expect(wrapper.get('form[aria-label="目的地"] button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button[aria-label="将状态改为 PLANNED"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.itinerary-day__header button').attributes('disabled')).toBeDefined()

    pending.resolve({ version: 2 })
    await flushPromises()
    expect(api.get).toHaveBeenCalledTimes(2)
    expect(wrapper.get('form[aria-label="目的地"] button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('命令成功但投影重读失败时明确提示已保存并提供重新加载', async () => {
    const api = {
      get: vi.fn()
        .mockResolvedValueOnce(detail())
        .mockRejectedValueOnce(new Error('网络暂不可用')),
      updateOverview: vi.fn().mockResolvedValue({ version: 2 }),
    }
    const wrapper = await mountEditor(api)

    await wrapper.get('form[aria-label="基本信息"]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('更改已保存，但最新内容未加载')
    expect(wrapper.get('button[aria-label="重新加载行程"]')).toBeTruthy()
    expect(wrapper.get('[role="status"]').text()).not.toContain('保存失败')
  })

  it('确认 AI 建议期间禁用手工编辑，成功后替换为正式行程快照', async () => {
    const confirmation = deferred()
    const refreshed = { ...detail(), title: 'AI 确认后的杭州周末', version: 2 }
    const api = { get: vi.fn().mockResolvedValueOnce(detail()).mockResolvedValueOnce(refreshed) }
    const planningApi = {
      getRequest: vi.fn().mockResolvedValue({
        id: '100', itineraryId: '10', version: 3, status: 'READY',
        startDate: '2026-09-01', endDate: '2026-09-02', budgetAmount: 2000,
        budgetCurrency: 'CNY', partySize: 2,
        preferences: { pace: 'BALANCED', tags: [], notes: '' },
        destinations: [{ name: '杭州', countryCode: 'CN', timeZone: 'Asia/Shanghai' }],
      }),
      listProposals: vi.fn().mockResolvedValue([{
        id: '200', requestId: '100', itineraryId: '10', baseItineraryVersion: 1,
        comparedItineraryVersion: 1, status: 'READY', summary: '新增早餐',
        knowledgeReferenceIds: [], operations: [{
          operationKey: 'add-breakfast', type: 'ADD_ITEM', summary: '新增早餐',
          targetDate: '2026-09-01', beforeItem: null,
          afterItem: { date: '2026-09-01', title: '早餐' }, dependencies: [],
        }],
      }]),
      confirm: vi.fn().mockReturnValue(confirmation.promise),
    }
    const wrapper = await mountEditor(api, planningApi)
    await wrapper.get('button[aria-label="打开 AI 行程规划"]').trigger('click')
    await flushPromises()
    await wrapper.get('button[aria-label="确认并写入行程"]').trigger('click')
    expect(wrapper.get('form[aria-label="基本信息"] button[type="submit"]').attributes('disabled')).toBeDefined()

    confirmation.resolve({ proposalId: '200', confirmed: true, resultVersion: 2, replayed: false })
    await flushPromises()
    expect(api.get).toHaveBeenCalledTimes(2)
    expect(wrapper.get('h1').text()).toContain('AI 确认后的杭州周末')
    expect(wrapper.get('form[aria-label="基本信息"] button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('可在地点抽屉中搜索并确认地点，保存后展示已确认地点且保留原地点名称', async () => {
    const selected = {
      source: 'AMAP', sourcePlaceId: 'B0FFG', name: '西湖', address: '杭州市西湖区龙井路',
      administrativeAreaCode: '330106', longitude: 120.13026, latitude: 30.23947, coordinateSystem: 'GCJ02',
    }
    const refreshed = detail()
    refreshed.version = 2
    refreshed.days[0].items[0] = {
      ...refreshed.days[0].items[0], placeName: '西湖边入口', placeReference: selected,
    }
    const api = { get: vi.fn().mockResolvedValueOnce(detail()).mockResolvedValueOnce(refreshed) }
    const mapApi = {
      searchPlaces: vi.fn().mockResolvedValue({ items: [selected] }),
      putPlace: vi.fn().mockResolvedValue({ version: 2 }),
    }
    const wrapper = await mountEditor(api, null, mapApi)

    await wrapper.get('button[aria-label="设置地点 西湖散步"]').trigger('click')
    await wrapper.get('input[aria-label="搜索地点"]').setValue('西湖')
    await wrapper.get('button[aria-label="搜索地点"]').trigger('click')
    await flushPromises()
    await wrapper.get('button[aria-label="选择地点 西湖"]').trigger('click')
    await wrapper.get('button[aria-label="确认地点"]').trigger('click')
    await flushPromises()

    expect(mapApi.putPlace).toHaveBeenCalledWith('10', '40', {
      commandId: expect.any(String), expectedVersion: 1, place: selected,
    })
    expect(wrapper.text()).toContain('西湖边入口')
    expect(wrapper.text()).toContain('杭州市西湖区龙井路')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
})

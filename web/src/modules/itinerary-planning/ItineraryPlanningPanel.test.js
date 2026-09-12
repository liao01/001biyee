import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import ItineraryPlanningPanel from './ItineraryPlanningPanel.vue'
import { itineraryPlanningHttpKey } from './itineraryPlanningHttp.js'

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

const itinerary = () => ({
  id: '42', title: '杭州周末', startDate: '2026-10-02', endDate: '2026-10-03',
  baseCurrency: 'CNY', version: 3,
  destinations: [{ name: '杭州', countryCode: 'CN', timeZone: 'Asia/Shanghai' }],
  days: [],
})

const request = () => ({
  id: '100', itineraryId: '42', version: 1, status: 'DRAFT',
  startDate: '2026-10-02', endDate: '2026-10-03', budgetAmount: 3000,
  budgetCurrency: 'CNY', partySize: 2,
  preferences: { pace: 'BALANCED', tags: ['CULTURE'], notes: '不早起' },
  destinations: [{ name: '杭州', countryCode: 'CN', timeZone: 'Asia/Shanghai' }],
})

const readyProposal = () => ({
  id: '200', requestId: '100', itineraryId: '42', baseItineraryVersion: 3,
  comparedItineraryVersion: 3, status: 'READY', summary: '知识库建议', projectedCost: 120,
  knowledgeReferenceIds: ['kb:guide:1'],
  operations: [
    {
      operationKey: 'add-breakfast', type: 'ADD_ITEM', summary: '增加早餐',
      targetDate: '2026-10-02', beforeItem: null,
      afterItem: { date: '2026-10-02', title: '早餐', startTime: '08:00', endTime: '09:00' },
      dependencies: [],
    },
    {
      operationKey: 'update-museum', type: 'UPDATE_ITEM', summary: '调整博物馆',
      targetDate: '2026-10-02',
      beforeItem: { date: '2026-10-02', title: '旧安排', startTime: '09:00', endTime: '10:00' },
      afterItem: { date: '2026-10-02', title: '博物馆', startTime: '10:00', endTime: '11:00' },
      dependencies: ['add-breakfast'],
    },
  ],
})

const missingPlanning = Object.assign(new Error('not found'), { errorCode: 'PLANNING_NOT_FOUND' })

const mountPanel = async (api, props = {}) => {
  const wrapper = mount(ItineraryPlanningPanel, {
    props: { itinerary: itinerary(), itineraryApi: { get: vi.fn() }, ...props },
    global: { provide: { [itineraryPlanningHttpKey]: api } },
  })
  await flushPromises()
  return wrapper
}

describe('AI 行程规划面板', () => {
  it('以正式行程默认值构建完整结构化需求并保存恢复', async () => {
    const api = {
      getRequest: vi.fn().mockRejectedValue(missingPlanning),
      saveRequest: vi.fn().mockResolvedValue(request()),
    }
    const wrapper = await mountPanel(api)

    expect(wrapper.get('#planning-start-date').element.value).toBe('2026-10-02')
    expect(wrapper.get('#planning-destination-0').element.value).toBe('杭州')
    await wrapper.get('#planning-budget').setValue('3000')
    await wrapper.get('#planning-party-size').setValue('2')
    await wrapper.get('#planning-preference-CULTURE').setValue(true)
    await wrapper.get('#planning-notes').setValue('不早起')
    await wrapper.get('form[aria-label="AI 行程规划需求"]').trigger('submit')
    await flushPromises()

    expect(api.saveRequest).toHaveBeenCalledWith('42', expect.objectContaining({
      requestId: null,
      expectedVersion: 0,
      draft: expect.objectContaining({
        budgetAmount: 3000,
        budgetCurrency: 'CNY',
        partySize: 2,
        preferences: { pace: 'BALANCED', tags: ['CULTURE'], notes: '不早起' },
      }),
    }))
    expect(wrapper.get('[role="status"]').text()).toContain('需求已保存')
  })

  it('显示生成中和可恢复失败，不把供应商细节展示给用户', async () => {
    const pending = deferred()
    const api = {
      getRequest: vi.fn().mockResolvedValue(request()),
      listProposals: vi.fn().mockResolvedValue([]),
      saveRequest: vi.fn().mockResolvedValue(request()),
      generate: vi.fn().mockReturnValue(pending.promise),
    }
    const wrapper = await mountPanel(api)
    await wrapper.get('button[aria-label="生成 AI 行程建议"]').trigger('click')
    await vi.waitFor(() => expect(api.generate).toHaveBeenCalledTimes(1))
    expect(wrapper.get('[aria-busy="true"]').text()).toContain('正在结合参考知识生成建议')
    expect(wrapper.get('button[aria-label="生成 AI 行程建议"]').attributes('disabled')).toBeDefined()

    pending.reject(Object.assign(new Error('secret provider stack'), {
      errorCode: 'PROVIDER_RATE_LIMITED', status: 429,
    }))
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('请求较多，请稍后重试')
    expect(wrapper.text()).not.toContain('secret provider stack')
  })

  it.each([
    ['PROVIDER_TIMEOUT', '生成超时'],
    ['INVALID_CONTRACT', '建议格式无效'],
    ['TIME_CONFLICT', '建议未通过校验'],
    ['BUDGET_EXCEEDED', '建议超出预算'],
  ])('将 %s 映射为可理解且可重试的界面状态', async (errorCode, expected) => {
    const api = {
      getRequest: vi.fn().mockResolvedValue(request()),
      listProposals: vi.fn().mockResolvedValue([]),
      saveRequest: vi.fn().mockResolvedValue(request()),
      generate: vi.fn().mockRejectedValue(Object.assign(new Error('supplier raw detail'), { errorCode })),
    }
    const wrapper = await mountPanel(api)
    await wrapper.get('button[aria-label="生成 AI 行程建议"]').trigger('click')
    await vi.waitFor(() => expect(wrapper.find('[role="alert"]').exists()).toBe(true))

    expect(wrapper.get('[role="alert"]').text()).toContain(expected)
    expect(wrapper.text()).not.toContain('supplier raw detail')
  })

  it('按日展示后端差异、知识参与说明和依赖选择，并支持全选全不选', async () => {
    const api = {
      getRequest: vi.fn().mockResolvedValue(request()),
      listProposals: vi.fn().mockResolvedValue([readyProposal()]),
    }
    const wrapper = await mountPanel(api)

    expect(wrapper.text()).toContain('参考知识已参与生成')
    expect(wrapper.text()).not.toContain('kb:guide:1')
    expect(wrapper.text()).toContain('修改前')
    expect(wrapper.text()).toContain('旧安排')
    expect(wrapper.text()).toContain('修改后')
    expect(wrapper.text()).toContain('博物馆')
    await wrapper.get('button[aria-label="取消选择全部建议"]').trigger('click')
    expect(wrapper.findAll('.planning-operation input[type="checkbox"]:checked')).toHaveLength(0)
    await wrapper.get('#planning-operation-update-museum').setValue(true)
    expect(wrapper.get('#planning-operation-add-breakfast').element.checked).toBe(true)
    expect(wrapper.text()).toContain('选择此项会同时保留 1 项依赖')
    await wrapper.get('button[aria-label="选择全部建议"]').trigger('click')
    expect(wrapper.findAll('.planning-operation input[type="checkbox"]:checked')).toHaveLength(2)
  })

  it('过期建议保留预览但禁用确认', async () => {
    const expired = { ...readyProposal(), status: 'EXPIRED', comparedItineraryVersion: 4 }
    const api = {
      getRequest: vi.fn().mockResolvedValue(request()),
      listProposals: vi.fn().mockResolvedValue([expired]),
    }
    const wrapper = await mountPanel(api)

    expect(wrapper.text()).toContain('行程已变化，请重新生成建议')
    expect(wrapper.get('button[aria-label="确认并写入行程"]').attributes('disabled')).toBeDefined()
  })

  it('展示删除与排序的后端前后差异，不在前端推断正式快照', async () => {
    const proposal = readyProposal()
    proposal.operations = [
      {
        operationKey: 'delete-old', type: 'DELETE_ITEM', summary: '移除旧安排',
        targetDate: '2026-10-02', beforeItem: { title: '旧安排' }, afterItem: null,
        dependencies: [],
      },
      {
        operationKey: 'reorder', type: 'REORDER_DAY_ITEMS', summary: '调整顺序',
        targetDate: '2026-10-02', beforeItemReferences: [{ existingItemId: '1' }, { existingItemId: '2' }],
        afterItemReferences: [{ existingItemId: '2' }, { existingItemId: '1' }], dependencies: [],
      },
    ]
    const api = {
      getRequest: vi.fn().mockResolvedValue(request()),
      listProposals: vi.fn().mockResolvedValue([proposal]),
    }
    const wrapper = await mountPanel(api)

    expect(wrapper.text()).toContain('将删除')
    expect(wrapper.text()).toContain('旧安排')
    expect(wrapper.text()).toContain('调整前 1 → 2')
    expect(wrapper.text()).toContain('调整后 2 → 1')
  })
})

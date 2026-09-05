import { describe, expect, it, vi } from 'vitest'

import { createItineraryPlanning } from './itineraryPlanning.js'

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

const request = (version = 1) => ({
  id: '100', itineraryId: '42', version, status: 'DRAFT',
  startDate: '2026-10-02', endDate: '2026-10-03', partySize: 2,
})

const proposal = () => ({
  id: '200', requestId: '100', itineraryId: '42', baseItineraryVersion: 3,
  status: 'READY', summary: '知识库建议',
  operations: [
    { operationKey: 'add-breakfast', summary: '增加早餐', dependencies: [] },
    { operationKey: 'add-museum', summary: '增加博物馆', dependencies: ['add-breakfast'] },
    { operationKey: 'reorder-day', summary: '调整顺序', dependencies: ['add-museum'] },
  ],
})

describe('AI 行程规划状态机', () => {
  it('恢复后端状态、保存草稿，并阻止同一生成动作重复发送', async () => {
    const pending = deferred()
    const api = {
      getRequest: vi.fn().mockResolvedValue(request()),
      listProposals: vi.fn().mockResolvedValue([]),
      saveRequest: vi.fn().mockResolvedValue(request(2)),
      generate: vi.fn().mockReturnValue(pending.promise),
    }
    const planning = createItineraryPlanning({ itineraryId: '42', api, itineraryApi: {}, uuid: vi.fn() })

    await planning.load()
    expect(planning.state.status).toBe('idle')
    await planning.saveDraft({ ...request(), preferences: {} })
    expect(api.saveRequest).toHaveBeenCalledWith('42', expect.objectContaining({
      requestId: '100', expectedVersion: 1,
    }))
    const first = planning.generate()
    const duplicate = planning.generate()
    expect(duplicate).toBe(first)
    expect(api.generate).toHaveBeenCalledTimes(1)
    expect(planning.state.status).toBe('generating')
    pending.resolve(proposal())
    await first
    expect(planning.state.status).toBe('ready')
    expect(planning.state.proposal.id).toBe('200')
  })

  it('选择操作时自动补齐依赖，取消依赖时移除所有依赖方', async () => {
    const api = {
      getRequest: vi.fn().mockResolvedValue(request()),
      listProposals: vi.fn().mockResolvedValue([proposal()]),
    }
    const planning = createItineraryPlanning({ itineraryId: '42', api, itineraryApi: {}, uuid: vi.fn() })
    await planning.load()
    planning.openProposal('200')

    planning.selectOperation('reorder-day', true)
    expect([...planning.state.selectedOperationKeys].sort()).toEqual([
      'add-breakfast', 'add-museum', 'reorder-day',
    ])
    planning.selectOperation('add-breakfast', false)
    expect([...planning.state.selectedOperationKeys]).toEqual([])
  })

  it('确认失败重试复用 decisionId 和 commandId，成功后刷新正式行程', async () => {
    const networkFailure = Object.assign(new Error('offline'), { status: 503 })
    const api = {
      getRequest: vi.fn().mockResolvedValue(request()),
      listProposals: vi.fn().mockResolvedValue([proposal()]),
      confirm: vi.fn().mockRejectedValueOnce(networkFailure).mockResolvedValueOnce({
        proposalId: '200', confirmed: true, resultVersion: 4, replayed: true,
      }),
    }
    const itineraryApi = { get: vi.fn().mockResolvedValue({ id: '42', version: 4 }) }
    const uuid = vi.fn().mockReturnValueOnce('decision-1').mockReturnValueOnce('command-1')
    const planning = createItineraryPlanning({ itineraryId: '42', api, itineraryApi, uuid })
    await planning.load()
    planning.openProposal('200')
    planning.selectOperation('add-museum', true)

    await expect(planning.confirm()).rejects.toThrow('offline')
    await planning.confirm()

    expect(api.confirm.mock.calls[0][2]).toEqual(api.confirm.mock.calls[1][2])
    expect(uuid).toHaveBeenCalledTimes(2)
    expect(itineraryApi.get).toHaveBeenCalledWith('42')
    expect(planning.state.itinerarySnapshot.version).toBe(4)
    expect(planning.state.status).toBe('confirmed')
  })

  it('将建议过期与普通失败分开，并在过期时刷新正式行程', async () => {
    const expired = Object.assign(new Error('expired'), {
      status: 409, errorCode: 'PROPOSAL_EXPIRED',
    })
    const api = {
      getRequest: vi.fn().mockResolvedValue(request()),
      listProposals: vi.fn().mockResolvedValue([proposal()]),
      confirm: vi.fn().mockRejectedValue(expired),
    }
    const itineraryApi = { get: vi.fn().mockResolvedValue({ id: '42', version: 9 }) }
    const planning = createItineraryPlanning({
      itineraryId: '42', api, itineraryApi, uuid: () => crypto.randomUUID(),
    })
    await planning.load()
    planning.openProposal('200')
    planning.selectOperation('add-breakfast', true)

    await expect(planning.confirm()).rejects.toThrow('expired')
    expect(planning.state.status).toBe('expired')
    expect(planning.state.itinerarySnapshot.version).toBe(9)
  })

  it('把后端持久化的失败建议转换为可展示的失败类别', async () => {
    const failedProposal = {
      ...proposal(), status: 'FAILED', failureCode: 'INVALID_CONTRACT', operations: [],
    }
    const api = {
      getRequest: vi.fn().mockResolvedValue(request()),
      listProposals: vi.fn().mockResolvedValue([]),
      generate: vi.fn().mockResolvedValue(failedProposal),
    }
    const planning = createItineraryPlanning({ itineraryId: '42', api, itineraryApi: {}, uuid: vi.fn() })
    await planning.load()

    await planning.generate()

    expect(planning.state.status).toBe('failed')
    expect(planning.state.error).toMatchObject({ errorCode: 'INVALID_CONTRACT' })
  })

  it('拒绝失败重试复用决定编号且绝不刷新或修改正式行程', async () => {
    const api = {
      getRequest: vi.fn().mockResolvedValue(request()),
      listProposals: vi.fn().mockResolvedValue([proposal()]),
      reject: vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({
        proposalId: '200', confirmed: false, resultVersion: null, replayed: true,
      }),
    }
    const itineraryApi = { get: vi.fn() }
    const uuid = vi.fn().mockReturnValue('reject-decision')
    const planning = createItineraryPlanning({ itineraryId: '42', api, itineraryApi, uuid })
    await planning.load()
    planning.openProposal('200')

    await expect(planning.reject()).rejects.toThrow('offline')
    await planning.reject()

    expect(api.reject.mock.calls[0][2]).toEqual(api.reject.mock.calls[1][2])
    expect(uuid).toHaveBeenCalledTimes(1)
    expect(itineraryApi.get).not.toHaveBeenCalled()
    expect(planning.state.status).toBe('idle')
  })
})

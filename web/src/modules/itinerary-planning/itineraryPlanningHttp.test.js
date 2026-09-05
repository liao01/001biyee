import { describe, expect, it } from 'vitest'

import { createItineraryPlanningHttp } from './itineraryPlanningHttp.js'

describe('AI 行程规划 HTTP 适配器', () => {
  it('为规划请求、生成、建议和决定使用正式身份保护路径', async () => {
    const calls = []
    const http = { request: async (config) => {
      calls.push(config)
      return { data: { success: true, content: { id: 9007199254740991n } } }
    } }
    const api = createItineraryPlanningHttp(http)
    const draft = { requestId: null, expectedVersion: 0, draft: { partySize: 2 } }
    const confirm = {
      decisionId: 'decision-1', commandId: 'command-1', expectedItineraryVersion: 3,
      selectedOperationKeys: ['add-one'],
    }

    await api.getRequest('42')
    await api.saveRequest('42', draft)
    await api.generate('42', { expectedVersion: 1 })
    await api.listProposals('42')
    await api.getProposal('42', '200')
    await api.confirm('42', '200', confirm)
    await api.reject('42', '200', { decisionId: 'decision-2' })

    expect(calls).toEqual([
      { method: 'get', url: '/lyw/web/itineraries/42/planning/request' },
      { method: 'put', url: '/lyw/web/itineraries/42/planning/request', data: draft },
      { method: 'post', url: '/lyw/web/itineraries/42/planning/generate', data: { expectedVersion: 1 } },
      { method: 'get', url: '/lyw/web/itineraries/42/planning/proposals' },
      { method: 'get', url: '/lyw/web/itineraries/42/planning/proposals/200' },
      { method: 'post', url: '/lyw/web/itineraries/42/planning/proposals/200/confirm', data: confirm },
      { method: 'post', url: '/lyw/web/itineraries/42/planning/proposals/200/reject', data: { decisionId: 'decision-2' } },
    ])
  })

  it('将所有服务端 ID 规范化为字符串并保留依赖字段', async () => {
    const http = { request: async () => ({ data: { success: true, content: {
      id: 9007199254740991n,
      itineraryId: 42,
      operations: [{ targetItemId: 7, itemReferences: [{ existingItemId: 8 }] }],
    } } }) }

    await expect(createItineraryPlanningHttp(http).getProposal('42', '1')).resolves.toEqual({
      id: '9007199254740991',
      itineraryId: '42',
      operations: [{ targetItemId: '7', itemReferences: [{ existingItemId: '8' }] }],
    })
  })

  it('暴露稳定的失败类别且不返回供应商原始信息', async () => {
    const http = { request: async () => {
      const error = new Error('provider stack and prompt')
      error.response = {
        status: 503,
        data: {
          success: false,
          message: 'AI 规划服务暂时不可用',
          content: { errorCode: 'PROVIDER_UNAVAILABLE' },
        },
      }
      throw error
    } }

    await expect(createItineraryPlanningHttp(http).generate('42', { expectedVersion: 1 }))
      .rejects.toMatchObject({
        name: 'ItineraryPlanningHttpError',
        message: 'AI 规划服务暂时不可用',
        status: 503,
        errorCode: 'PROVIDER_UNAVAILABLE',
      })
  })
})

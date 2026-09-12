import { describe, expect, it } from 'vitest'

import { createItineraryHttp } from './itineraryHttp.js'

describe('行程 HTTP 适配器', () => {
  it('只通过语义方法发送统一命令外壳并规范化 ID', async () => {
    const calls = []
    const http = {
      async request(config) {
        calls.push(config)
        return { data: { success: true, content: { itineraryId: 9007199254740991n, itemId: 8 } } }
      },
    }
    const api = createItineraryHttp(http)
    const command = { commandId: 'cmd-1', expectedVersion: 3, payload: { title: '杭州' } }

    const result = await api.updateOverview('42', command)

    expect(calls).toEqual([{ method: 'patch', url: '/lyw/web/itineraries/42', data: command }])
    expect(result).toEqual({ itineraryId: '9007199254740991', itemId: '8' })
    expect(Object.keys(api).sort()).toEqual([
      'addItem', 'create', 'deleteItem', 'get', 'list', 'reorderItems',
      'replaceDestinations', 'transition', 'updateItem', 'updateOverview',
    ])
  })

  it('保留共享身份客户端注入的 Bearer 头且暴露稳定服务端错误', async () => {
    const http = {
      async request(config) {
        config.headers = { Authorization: 'Bearer shared-session-token' }
        const error = new Error('conflict')
        error.response = {
          status: 409,
          data: { success: false, message: '行程已被更新，请重新加载', content: { errorCode: 'VERSION_CONFLICT' } },
        }
        throw error
      },
    }

    await expect(createItineraryHttp(http).get('42')).rejects.toMatchObject({
      name: 'ItineraryHttpError', status: 409, errorCode: 'VERSION_CONFLICT',
    })
  })

  it('为所有端点使用正式路径和 HTTP 动词', async () => {
    const calls = []
    const http = { request: async (config) => {
      calls.push(`${config.method} ${config.url}`)
      return { data: { success: true, content: {} } }
    } }
    const api = createItineraryHttp(http)
    const command = { commandId: 'cmd', expectedVersion: 1, payload: {} }
    await api.list({ status: ['DRAFT'], cursor: 'next', limit: 10 })
    await api.create(command)
    await api.get('1')
    await api.replaceDestinations('1', command)
    await api.addItem('1', command)
    await api.updateItem('1', '2', command)
    await api.deleteItem('1', '2', command)
    await api.reorderItems('1', '3', command)
    await api.transition('1', command)

    expect(calls).toEqual([
      'get /lyw/web/itineraries', 'post /lyw/web/itineraries', 'get /lyw/web/itineraries/1',
      'put /lyw/web/itineraries/1/destinations', 'post /lyw/web/itineraries/1/items',
      'patch /lyw/web/itineraries/1/items/2', 'delete /lyw/web/itineraries/1/items/2',
      'put /lyw/web/itineraries/1/days/3/item-order',
      'post /lyw/web/itineraries/1/status-transitions',
    ])
  })
})

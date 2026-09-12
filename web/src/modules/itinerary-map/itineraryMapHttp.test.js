import { describe, expect, it } from 'vitest'

import { ItineraryMapHttpError, createItineraryMapHttp } from './itineraryMapHttp.js'

describe('行程地图 HTTP 适配器', () => {
  it('只通过正式端点查询、确认、移除地点和读取每日地图，并规范化标识', async () => {
    const calls = []
    const http = { request: async (config) => {
      calls.push(config)
      return { data: { success: true, content: {
        itineraryId: 9007199254740991n,
        itemId: 8,
        stops: [{ itemId: 9 }],
      } } }
    } }
    const api = createItineraryMapHttp(http)
    const command = { commandId: 'cmd-1', expectedVersion: 3, payload: { name: '西湖' } }

    const searched = await api.searchPlaces({ keyword: '西湖', city: '杭州', limit: 5 })
    await api.putPlace('42', '8', command)
    await api.removePlace('42', '8', { commandId: 'cmd-2', expectedVersion: 4 })
    const map = await api.dailyMap('42', '30', 'TRANSIT')

    expect(searched).toMatchObject({ itineraryId: '9007199254740991', itemId: '8' })
    expect(map.stops[0].itemId).toBe('9')
    expect(calls).toEqual([
      { method: 'get', url: '/lyw/web/places/search', params: { keyword: '西湖', city: '杭州', limit: 5 } },
      { method: 'put', url: '/lyw/web/itineraries/42/items/8/place', data: command },
      { method: 'delete', url: '/lyw/web/itineraries/42/items/8/place', data: { commandId: 'cmd-2', expectedVersion: 4 } },
      { method: 'get', url: '/lyw/web/itineraries/42/days/30/map', params: { mode: 'TRANSIT' } },
    ])
  })

  it('保留服务端失败信息，网络失败不泄露原始响应体', async () => {
    const http = { request: async () => {
      const error = new Error('request failed')
      error.response = {
        status: 503,
        data: { success: false, message: '地图服务暂不可用', content: { errorCode: 'MAP_UNAVAILABLE' } },
      }
      throw error
    } }

    await expect(createItineraryMapHttp(http).searchPlaces({ keyword: '西湖' }))
      .rejects.toMatchObject({
        name: 'ItineraryMapHttpError', status: 503, errorCode: 'MAP_UNAVAILABLE', message: '地图服务暂不可用',
      })
    expect(ItineraryMapHttpError).toBeTypeOf('function')
  })
})

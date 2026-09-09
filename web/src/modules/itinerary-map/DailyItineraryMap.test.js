import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import DailyItineraryMap from './DailyItineraryMap.vue'

const view = (mode = 'WALKING') => ({
  itineraryId: '10', dayId: '30', mode, degraded: true,
  stops: [
    { sequence: 1, itemId: '40', title: '西湖散步', placeName: '西湖', status: 'RESOLVED', placeReference: { name: '西湖', longitude: 120.13026, latitude: 30.23947 } },
    { sequence: 2, itemId: '41', title: '午餐', placeName: '湖畔餐厅', status: 'UNRESOLVED', placeReference: null },
    { sequence: 3, itemId: '42', title: '博物馆', placeName: '省博', status: 'RESOLVED', placeReference: { name: '省博', longitude: 120.152, latitude: 30.269 } },
  ],
  legs: [
    { fromItemId: '40', toItemId: '41', status: 'LIVE', distanceMeters: 850, durationSeconds: 600, stale: false, polyline: [{ longitude: 120.13026, latitude: 30.23947 }, { longitude: 120.14, latitude: 30.25 }] },
    { fromItemId: '41', toItemId: '42', status: 'UNAVAILABLE', unavailableReason: 'ROUTE_PROVIDER_UNAVAILABLE', stale: false, polyline: [] },
  ],
})

const createAmap = () => {
  const map = { setFitView: vi.fn(), setZoomAndCenter: vi.fn(), destroy: vi.fn() }
  const Marker = vi.fn(function Marker(options) { this.options = options; this.setMap = vi.fn() })
  const Polyline = vi.fn(function Polyline(options) { this.options = options; this.setMap = vi.fn() })
  return { amap: { Map: vi.fn(() => map), Marker, Polyline }, map, Marker, Polyline }
}

describe('每日行程地图', () => {
  it('展开后才请求，按停靠顺序绘制已解析地点和可用路线，切换模式会重新请求并卸载地图', async () => {
    const { amap, map, Marker, Polyline } = createAmap()
    const mapApi = { dailyMap: vi.fn(async (_itineraryId, _dayId, mode) => view(mode)) }
    const wrapper = mount(DailyItineraryMap, { props: {
      open: false, itineraryId: '10', dayId: '30', mapApi, mapLoader: vi.fn().mockResolvedValue(amap),
    } })
    expect(mapApi.dailyMap).not.toHaveBeenCalled()

    await wrapper.setProps({ open: true })
    await flushPromises()
    await flushPromises()
    expect(mapApi.dailyMap).toHaveBeenCalledWith('10', '30', 'WALKING')
    expect(Marker).toHaveBeenCalledTimes(2)
    expect(Polyline).toHaveBeenCalledTimes(1)
    expect(map.setFitView).toHaveBeenCalled()
    expect(wrapper.text()).toContain('部分路线暂不可用')
    expect(wrapper.text()).toContain('午餐 · 暂未确认地图地点')

    await wrapper.get('button[aria-label="切换为驾车"]').trigger('click')
    await flushPromises()
    await flushPromises()
    expect(mapApi.dailyMap).toHaveBeenLastCalledWith('10', '30', 'DRIVING')
    wrapper.unmount()
    expect(map.destroy).toHaveBeenCalledTimes(1)
  })

  it('图层加载失败时仍保留地点和路线文字卡片', async () => {
    const single = view()
    single.stops = [single.stops[0]]
    single.legs = []
    const mapApi = { dailyMap: vi.fn().mockResolvedValue(single) }
    const wrapper = mount(DailyItineraryMap, { props: {
      open: true, itineraryId: '10', dayId: '30', mapApi,
      mapLoader: vi.fn().mockRejectedValue(new Error('地图脚本加载失败')),
    } })
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('地图图层暂不可用')
    expect(wrapper.text()).toContain('1. 西湖散步')
  })

  it('只有一个已解析地点时居中显示该地点', async () => {
    const { amap, map } = createAmap()
    const single = view()
    single.stops = [single.stops[0]]
    single.legs = []
    const wrapper = mount(DailyItineraryMap, { props: {
      open: true, itineraryId: '10', dayId: '30', mapApi: { dailyMap: vi.fn().mockResolvedValue(single) },
      mapLoader: vi.fn().mockResolvedValue(amap),
    } })
    await flushPromises()
    await flushPromises()

    expect(map.setZoomAndCenter).toHaveBeenCalledWith(14, [120.13026, 30.23947])
    wrapper.unmount()
  })
})

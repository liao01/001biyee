import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import RouteLegList from './RouteLegList.vue'

const stops = [
  { sequence: 1, itemId: '40', title: '西湖散步', placeName: '西湖', status: 'RESOLVED' },
  { sequence: 2, itemId: '41', title: '午餐', placeName: '湖畔餐厅', status: 'UNRESOLVED' },
  { sequence: 3, itemId: '42', title: '博物馆', placeName: '省博', status: 'RESOLVED' },
]

describe('每日路线文字卡片', () => {
  it('始终按停靠顺序呈现文字信息，并显示实时、缓存、陈旧和不可用路线', () => {
    const wrapper = mount(RouteLegList, { props: {
      stops,
      legs: [
        { fromItemId: '40', toItemId: '41', status: 'LIVE', distanceMeters: 850, durationSeconds: 3540, stale: false },
        { fromItemId: '41', toItemId: '42', status: 'CACHED', distanceMeters: 1250, durationSeconds: 3900, stale: true },
        { fromItemId: '42', toItemId: '43', status: 'UNAVAILABLE', unavailableReason: 'ROUTE_PROVIDER_UNAVAILABLE', stale: false },
      ],
    } })

    expect(wrapper.get('[aria-label="每日路线文字说明"]').text()).toContain('1. 西湖散步')
    expect(wrapper.text()).toContain('2. 午餐 · 暂未确认地图地点')
    expect(wrapper.text()).toContain('850 米 · 59 分钟 · 实时路线')
    expect(wrapper.text()).toContain('1.3 公里 · 1 小时 5 分钟 · 缓存路线（可能不是最新）')
    expect(wrapper.text()).toContain('暂时无法获取路线')
  })
})

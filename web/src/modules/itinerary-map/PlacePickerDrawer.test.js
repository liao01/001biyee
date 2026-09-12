import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import PlacePickerDrawer from './PlacePickerDrawer.vue'

const candidate = () => ({
  source: 'AMAP', sourcePlaceId: 'B0FFG', name: '西湖', address: '杭州市西湖区龙井路',
  administrativeAreaCode: '330106', longitude: 120.13026, latitude: 30.23947, coordinateSystem: 'GCJ02',
})

const mountDrawer = (props = {}) => mount(PlacePickerDrawer, {
  attachTo: document.body,
  props: {
    open: true,
    item: { id: '40', title: '西湖散步', placeName: '西湖边入口' },
    city: '杭州',
    mapApi: { searchPlaces: vi.fn().mockResolvedValue({ items: [candidate()] }) },
    saving: false,
    ...props,
  },
})

describe('地点选择抽屉', () => {
  it('空白关键词不请求服务；候选地点只在选中并确认后才发出地点数据', async () => {
    const mapApi = { searchPlaces: vi.fn().mockResolvedValue({ items: [candidate()] }) }
    const wrapper = mountDrawer({ mapApi })
    await wrapper.get('input[aria-label="搜索地点"]').setValue('   ')
    await wrapper.get('button[aria-label="搜索地点"]').trigger('click')
    expect(mapApi.searchPlaces).not.toHaveBeenCalled()

    await wrapper.get('input[aria-label="搜索地点"]').setValue('西湖')
    await wrapper.get('button[aria-label="搜索地点"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('杭州市西湖区龙井路')
    await wrapper.get('button[aria-label="选择地点 西湖"]').trigger('click')
    expect(wrapper.emitted('confirm')).toBeUndefined()
    await wrapper.get('button[aria-label="确认地点"]').trigger('click')
    expect(wrapper.emitted('confirm')[0]).toEqual([candidate()])
  })

  it('地图服务失败时保留关键词、显示错误，并允许手工录入合法 GCJ-02 坐标', async () => {
    const mapApi = { searchPlaces: vi.fn().mockRejectedValue(new Error('地图服务暂不可用')) }
    const wrapper = mountDrawer({ mapApi })
    await wrapper.get('input[aria-label="搜索地点"]').setValue('西湖')
    await wrapper.get('button[aria-label="搜索地点"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('input[aria-label="搜索地点"]').element.value).toBe('西湖')
    expect(wrapper.get('[role="alert"]').text()).toContain('地图服务暂不可用')
    await wrapper.get('button[aria-label="确认地点"]').trigger('click')
    expect(wrapper.emitted('confirm')).toBeUndefined()
    expect(wrapper.text()).toContain('请填写地点名称，以及有效的经度和纬度。')
    await wrapper.get('input[aria-label="手工地点名称"]').setValue('湖畔咖啡')
    await wrapper.get('input[aria-label="经度"]').setValue('181')
    await wrapper.get('input[aria-label="纬度"]').setValue('30')
    await wrapper.get('button[aria-label="确认地点"]').trigger('click')
    expect(wrapper.emitted('confirm')).toBeUndefined()
    await wrapper.get('input[aria-label="经度"]').setValue('120.13026')
    await wrapper.get('input[aria-label="纬度"]').setValue('30.23947')
    await wrapper.get('button[aria-label="确认地点"]').trigger('click')
    expect(wrapper.emitted('confirm')[0]).toEqual([{
      source: 'MANUAL', sourcePlaceId: null, name: '湖畔咖啡', address: null,
      administrativeAreaCode: null, longitude: 120.13026, latitude: 30.23947, coordinateSystem: 'GCJ02',
    }])
  })

  it('保存中不能重复确认，关闭抽屉后焦点会回到原来的触发按钮', async () => {
    const opener = document.createElement('button')
    opener.textContent = '设置地点'
    document.body.append(opener)
    opener.focus()
    const wrapper = mountDrawer({ open: false, saving: true })
    await wrapper.setProps({ open: true })
    await flushPromises()
    expect(document.activeElement).toBe(wrapper.get('input[aria-label="搜索地点"]').element)
    expect(wrapper.get('button[aria-label="确认地点"]').attributes('disabled')).toBeDefined()

    await wrapper.get('button[aria-label="关闭地点选择"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
    await wrapper.setProps({ open: false })
    await flushPromises()
    expect(document.activeElement).toBe(opener)
    opener.remove()
  })
})

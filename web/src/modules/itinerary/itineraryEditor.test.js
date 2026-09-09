import { describe, expect, it, vi } from 'vitest'

import { createItineraryEditor } from './itineraryEditor.js'

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

const snapshot = () => ({
  id: '10', title: '原行程', version: 1, status: 'DRAFT',
  destinations: [{ id: '20', name: '杭州' }],
  days: [{ id: '30', date: '2026-09-01', items: [] }],
})

const snapshotWithPlaceableItem = () => ({
  ...snapshot(),
  days: [{ id: '30', date: '2026-09-01', items: [{
    id: '40', dayId: '30', title: '西湖散步', placeName: '西湖边入口',
    placeReference: null,
  }] }],
})

const westLake = () => ({
  source: 'AMAP', sourcePlaceId: 'B0FFG', name: '西湖', address: '杭州市西湖区',
  administrativeAreaCode: '330106', longitude: 120.13026, latitude: 30.23947, coordinateSystem: 'GCJ02',
})

describe('行程编辑器命令队列', () => {
  it('严格串行，每个动作只生成一个 UUID，后续命令采用上一响应版本', async () => {
    const first = deferred()
    const calls = []
    const api = {
      updateOverview: vi.fn((id, command) => { calls.push({ id, command }); return first.promise }),
      transition: vi.fn(async (id, command) => { calls.push({ id, command }); return { version: 3 } }),
    }
    const uuid = vi.fn().mockReturnValueOnce('uuid-1').mockReturnValueOnce('uuid-2')
    const editor = createItineraryEditor({ initialSnapshot: snapshot(), api, uuid })

    const overview = editor.updateOverview({ title: '新标题' })
    const transition = editor.transition('PLANNED')
    expect(editor.state.snapshot.title).toBe('新标题')
    expect(editor.state.status).toBe('saving')
    expect(api.transition).not.toHaveBeenCalled()
    first.resolve({ version: 2 })
    await overview
    await transition

    expect(calls.map(({ command }) => [command.commandId, command.expectedVersion])).toEqual([
      ['uuid-1', 1], ['uuid-2', 2],
    ])
    expect(uuid).toHaveBeenCalledTimes(2)
    expect(editor.state.snapshot.version).toBe(3)
    expect(editor.state.status).toBe('saved')
  })

  it('普通失败回滚乐观投影，手动重试复用同一 UUID', async () => {
    const timeout = Object.assign(new Error('timeout'), { code: 'ECONNABORTED' })
    const api = { updateOverview: vi.fn()
      .mockRejectedValueOnce(timeout)
      .mockResolvedValueOnce({ version: 2 }) }
    const editor = createItineraryEditor({ initialSnapshot: snapshot(), api, uuid: () => 'same-uuid' })

    await expect(editor.updateOverview({ title: '暂存标题' })).rejects.toThrow('timeout')
    expect(editor.state.snapshot.title).toBe('原行程')
    expect(editor.state.status).toBe('error')
    await editor.retryFailed()

    expect(api.updateOverview.mock.calls.map(([, command]) => command.commandId))
      .toEqual(['same-uuid', 'same-uuid'])
    expect(editor.state.snapshot.title).toBe('暂存标题')
    expect(editor.state.status).toBe('saved')
  })

  it('新增条目的乐观临时 ID 复用命令 UUID，成功后替换为服务端字符串 ID', async () => {
    const uuid = vi.fn().mockReturnValue('add-uuid')
    const api = { addItem: vi.fn().mockResolvedValue({ version: 2, itemId: 9001 }) }
    const editor = createItineraryEditor({ initialSnapshot: snapshot(), api, uuid })

    await editor.addItem({ dayId: '30', title: '西湖散步' })

    expect(uuid).toHaveBeenCalledTimes(1)
    expect(api.addItem.mock.calls[0][1].commandId).toBe('add-uuid')
    expect(editor.state.snapshot.days[0].items[0].id).toBe('9001')
  })

  it('409 停止后续命令并重载完整服务端快照', async () => {
    const conflict = Object.assign(new Error('conflict'), { status: 409, errorCode: 'VERSION_CONFLICT' })
    const api = {
      updateOverview: vi.fn().mockRejectedValue(conflict),
      transition: vi.fn(),
      get: vi.fn().mockResolvedValue({ ...snapshot(), title: '服务端标题', version: 7 }),
    }
    const editor = createItineraryEditor({ initialSnapshot: snapshot(), api, uuid: () => crypto.randomUUID() })
    const failed = editor.updateOverview({ title: '本地标题' })
    const stopped = editor.transition('PLANNED')
    await expect(failed).rejects.toThrow('conflict')
    await expect(stopped).rejects.toMatchObject({ errorCode: 'VERSION_CONFLICT' })
    expect(api.transition).not.toHaveBeenCalled()
    expect(editor.state.status).toBe('conflict')

    await editor.reload()
    expect(editor.state.snapshot.title).toBe('服务端标题')
    expect(editor.state.snapshot.version).toBe(7)
    expect(editor.state.status).toBe('idle')
  })

  it('仅在命令在途时注册离开确认，失败不能显示为已保存', async () => {
    const pending = deferred()
    const add = vi.spyOn(window, 'addEventListener')
    const remove = vi.spyOn(window, 'removeEventListener')
    const editor = createItineraryEditor({
      initialSnapshot: snapshot(), api: { updateOverview: () => pending.promise }, uuid: () => 'uuid',
    })
    const operation = editor.updateOverview({ title: '在途' })
    expect(add).toHaveBeenCalledWith('beforeunload', expect.any(Function))
    pending.reject(new Error('failed'))
    await expect(operation).rejects.toThrow('failed')
    expect(remove).toHaveBeenCalledWith('beforeunload', expect.any(Function))
    expect(editor.state.status).toBe('error')
  })

  it('确认和移除地点复用命令队列，乐观更新地点引用但不删除用户的地点名称', async () => {
    const confirmation = deferred()
    const calls = []
    const api = {
      putPlace: vi.fn((id, itemId, command) => { calls.push({ id, itemId, command }); return confirmation.promise }),
      removePlace: vi.fn(async (id, itemId, command) => { calls.push({ id, itemId, command }); return { version: 3 } }),
    }
    const uuid = vi.fn().mockReturnValueOnce('place-uuid').mockReturnValueOnce('remove-uuid')
    const editor = createItineraryEditor({ initialSnapshot: snapshotWithPlaceableItem(), api, uuid })

    const put = editor.putPlace('40', westLake())
    const remove = editor.removePlace('40')
    expect(editor.state.snapshot.days[0].items[0]).toMatchObject({ placeName: '西湖边入口', placeReference: null })

    confirmation.resolve({ version: 2 })
    await put
    await remove

    expect(calls).toEqual([
      { id: '10', itemId: '40', command: { commandId: 'place-uuid', expectedVersion: 1, place: westLake() } },
      { id: '10', itemId: '40', command: { commandId: 'remove-uuid', expectedVersion: 2 } },
    ])
    expect(editor.state.snapshot.days[0].items[0]).toMatchObject({ placeName: '西湖边入口', placeReference: null })
    expect(editor.state.snapshot.version).toBe(3)
  })

  it('地点确认的普通失败回滚乐观地点引用，409 会停止后续地点操作并允许重载', async () => {
    const unavailable = Object.assign(new Error('地图服务暂不可用'), { status: 503 })
    const api = {
      putPlace: vi.fn().mockRejectedValueOnce(unavailable),
      get: vi.fn().mockResolvedValue({ ...snapshotWithPlaceableItem(), version: 7 }),
    }
    const editor = createItineraryEditor({ initialSnapshot: snapshotWithPlaceableItem(), api, uuid: () => 'place-uuid' })

    await expect(editor.putPlace('40', westLake())).rejects.toThrow('地图服务暂不可用')
    expect(editor.state.snapshot.days[0].items[0]).toMatchObject({ placeName: '西湖边入口', placeReference: null })
    expect(editor.state.status).toBe('error')

    const conflict = Object.assign(new Error('版本冲突'), { status: 409, errorCode: 'VERSION_CONFLICT' })
    const conflictApi = {
      putPlace: vi.fn().mockRejectedValue(conflict),
      removePlace: vi.fn(),
      get: vi.fn().mockResolvedValue({ ...snapshotWithPlaceableItem(), version: 7 }),
    }
    const conflicted = createItineraryEditor({ initialSnapshot: snapshotWithPlaceableItem(), api: conflictApi, uuid: () => crypto.randomUUID() })
    const put = conflicted.putPlace('40', westLake())
    const remove = conflicted.removePlace('40')
    await expect(put).rejects.toThrow('版本冲突')
    await expect(remove).rejects.toMatchObject({ errorCode: 'VERSION_CONFLICT' })
    expect(conflictApi.removePlace).not.toHaveBeenCalled()
    expect(conflicted.state.status).toBe('conflict')
    await conflicted.reload()
    expect(conflicted.state.snapshot.version).toBe(7)
  })
})

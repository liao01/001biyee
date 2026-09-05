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
})

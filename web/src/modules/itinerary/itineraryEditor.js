import { reactive } from 'vue'

const clone = (value) => structuredClone(value)

const findDay = (snapshot, dayId) => snapshot.days.find((day) => day.id === String(dayId))

const findItem = (snapshot, itemId) => {
  for (const day of snapshot.days) {
    const index = day.items.findIndex((item) => item.id === String(itemId))
    if (index >= 0) return { day, index, item: day.items[index] }
  }
  return null
}

const replaceSnapshot = (state, snapshot) => {
  state.snapshot = clone(snapshot)
}

export function createItineraryEditor({ initialSnapshot, api, uuid }) {
  let committed = clone(initialSnapshot)
  let queue = []
  let active = null
  let failed = null
  let listening = false
  const state = reactive({
    snapshot: clone(initialSnapshot),
    status: 'idle',
    error: null,
    pendingCount: 0,
  })

  const beforeUnload = (event) => {
    event.preventDefault()
    event.returnValue = ''
  }

  const syncPending = () => {
    state.pendingCount = queue.length + (active ? 1 : 0)
    const shouldListen = state.pendingCount > 0
    if (shouldListen && !listening) {
      window.addEventListener('beforeunload', beforeUnload)
      listening = true
    } else if (!shouldListen && listening) {
      window.removeEventListener('beforeunload', beforeUnload)
      listening = false
    }
  }

  const project = (entries = queue) => {
    const projected = clone(committed)
    for (const entry of entries) entry.apply(projected, entry.commandId)
    replaceSnapshot(state, projected)
  }

  const stopForConflict = (error) => {
    for (const queued of queue) queued.reject(error)
    queue = []
    failed = null
    state.status = 'conflict'
    state.error = error
  }

  const process = async () => {
    if (active || failed || queue.length === 0) return
    active = queue.shift()
    state.status = 'saving'
    state.error = null
    syncPending()
    const command = {
      commandId: active.commandId,
      expectedVersion: committed.version,
      payload: active.payload,
    }
    try {
      const result = await active.invoke(command)
      active.apply(committed, active.commandId)
      active.onSuccess?.(committed, result)
      committed.version = result.version
      active.resolve(result)
      active = null
      project()
      state.status = queue.length ? 'saving' : 'saved'
      syncPending()
      void process()
    } catch (error) {
      const rejected = active
      active = null
      replaceSnapshot(state, committed)
      rejected.reject(error)
      if (error.status === 409) {
        stopForConflict(error)
      } else {
        failed = rejected
        project()
        state.status = 'error'
        state.error = error
      }
      syncPending()
    }
  }

  const enqueue = ({ payload, apply, invoke, onSuccess }) => {
    const commandId = uuid()
    let resolve
    let reject
    const promise = new Promise((yes, no) => { resolve = yes; reject = no })
    const entry = { commandId, payload, apply, invoke, onSuccess, resolve, reject }
    queue.push(entry)
    apply(state.snapshot, commandId)
    state.status = 'saving'
    syncPending()
    void process()
    return promise
  }

  const itineraryId = String(initialSnapshot.id)
  const editor = {
    state,
    updateOverview: (patch) => enqueue({
      payload: patch,
      apply: (snapshot) => Object.assign(snapshot, patch),
      invoke: (command) => api.updateOverview(itineraryId, command),
    }),
    replaceDestinations: (destinations) => enqueue({
      payload: { destinations },
      apply: (snapshot) => { snapshot.destinations = clone(destinations) },
      invoke: (command) => api.replaceDestinations(itineraryId, command),
    }),
    addItem: (item) => {
      let temporaryId
      return enqueue({
        payload: item,
        apply: (snapshot, commandId) => {
          temporaryId ||= `pending:${commandId}`
          const day = findDay(snapshot, item.dayId)
          if (day) day.items.push({ ...clone(item), id: temporaryId, dayId: String(item.dayId) })
        },
        onSuccess: (snapshot, result) => {
          const pending = findItem(snapshot, temporaryId)
          if (pending && result.itemId) pending.item.id = String(result.itemId)
        },
        invoke: (command) => api.addItem(itineraryId, command),
      })
    },
    updateItem: (itemId, patch) => enqueue({
      payload: patch,
      apply: (snapshot) => {
        const existing = findItem(snapshot, itemId)
        if (!existing) return
        const nextDayId = String(patch.dayId ?? existing.day.id)
        const updated = { ...existing.item, ...clone(patch), dayId: nextDayId }
        existing.day.items.splice(existing.index, 1)
        findDay(snapshot, nextDayId)?.items.push(updated)
      },
      invoke: (command) => api.updateItem(itineraryId, String(itemId), command),
    }),
    deleteItem: (itemId) => enqueue({
      payload: {},
      apply: (snapshot) => {
        const existing = findItem(snapshot, itemId)
        if (existing) existing.day.items.splice(existing.index, 1)
      },
      invoke: (command) => api.deleteItem(itineraryId, String(itemId), command),
    }),
    reorderItems: (dayId, itemIds) => enqueue({
      payload: { itemIds: itemIds.map(String) },
      apply: (snapshot) => {
        const day = findDay(snapshot, dayId)
        if (!day) return
        const items = new Map(day.items.map((item) => [item.id, item]))
        day.items = itemIds.map((id) => items.get(String(id))).filter(Boolean)
      },
      invoke: (command) => api.reorderItems(itineraryId, String(dayId), command),
    }),
    transition: (toStatus) => enqueue({
      payload: { toStatus },
      apply: (snapshot) => { snapshot.status = toStatus },
      invoke: (command) => api.transition(itineraryId, command),
    }),
    async reload() {
      const fresh = await api.get(itineraryId)
      const cancellation = new Error('行程已重新加载，待处理命令已取消')
      for (const entry of queue) entry.reject(cancellation)
      queue = []
      failed = null
      committed = clone(fresh)
      replaceSnapshot(state, fresh)
      state.status = 'idle'
      state.error = null
      syncPending()
      return fresh
    },
    retryFailed() {
      if (!failed) return Promise.resolve(null)
      const retry = failed
      failed = null
      queue.unshift(retry)
      project()
      syncPending()
      void process()
      return new Promise((resolve, reject) => {
        const originalResolve = retry.resolve
        const originalReject = retry.reject
        retry.resolve = (value) => { originalResolve(value); resolve(value) }
        retry.reject = (error) => { originalReject(error); reject(error) }
      })
    },
  }
  return editor
}

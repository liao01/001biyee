import { expect, it, vi } from 'vitest'

it('不恢复旧会话存储，展示状态也不能持久化凭据', async () => {
  sessionStorage.setItem('member', JSON.stringify({ id: '42', token: 'TEST-obsolete' }))
  vi.resetModules()
  const { default: store } = await import('./index.js')
  expect(store.state.member).toEqual({})
  expect(sessionStorage.getItem('member')).toBeNull()
  store.commit('setMember', { id: '42', name: 'TEST 旅行者', accessToken: 'TEST-access', token: 'TEST-obsolete' })
  expect(store.state.member).toEqual({ id: '42', name: 'TEST 旅行者' })
  expect(sessionStorage.getItem('member')).toBeNull()
  store.commit('clearMember')
  expect(store.state.member).toEqual({})
})

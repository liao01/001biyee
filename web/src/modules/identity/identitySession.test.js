import axios, { AxiosError } from 'axios'
import { describe, expect, it } from 'vitest'
import { createIdentityHttp } from './identityHttp.js'
import { createIdentitySession } from './identitySession.js'

const response = (config, content) => ({ status: 200, data: { success: true, content }, config })
const unauthorized = (config) => Promise.reject(new AxiosError('TEST unauthorized', 'ERR_BAD_REQUEST', config, null,
  { status: 401, data: {}, config }))

function setup(handler) {
  const requests = []
  const members = []
  const expired = []
  const adapter = (config) => { requests.push(config); return handler(config) }
  const identity = createIdentityHttp(axios.create({ adapter }))
  const api = axios.create({ adapter })
  const session = createIdentitySession({ identity, onMember: (member) => members.push(member),
    onExpired: () => expired.push(true), apiBase: '', origin: 'http://localhost:5173' })
  session.install(api)
  return { api, session, requests, members, expired }
}

describe('邮箱身份会话', () => {
  it('匿名启动不弹窗，随后访问受保护内容才提示登录且不重复刷新', async () => {
    const { api, session, requests, expired } = setup(unauthorized)
    await session.restore()
    expect(expired).toEqual([])
    await Promise.allSettled([api.get('/lyw/web/first'), api.get('/lyw/web/second')])
    expect(expired).toEqual([true])
    expect(requests.filter((request) => request.url.endsWith('/refresh'))).toHaveLength(1)
  })
  it('刷新后仍未授权只重试一次并清空失效会话', async () => {
    const { api, session, members, expired, requests } = setup(async (config) => {
      if (config.url.endsWith('/login') || config.url.endsWith('/refresh')) return response(config, { accessToken: 'TEST-access' })
      if (config.url.endsWith('/me')) return response(config, { id: '42', name: 'TEST 旅行者' })
      return unauthorized(config)
    })
    await session.login({ email: 'test@example.com', password: 'TEST-password-123' })
    await expect(api.get('/lyw/web/private')).rejects.toThrow()
    expect(requests.filter((request) => request.url.endsWith('/refresh'))).toHaveLength(1)
    expect(requests.filter((request) => request.url.endsWith('/private'))).toHaveLength(2)
    expect(members.at(-1)).toEqual({})
    expect(expired).toEqual([true])
  })
  it('启动时通过 Cookie 恢复会话，匿名访客失败时不弹出登录', async () => {
    const restored = setup(async (config) => config.url.endsWith('/refresh')
      ? response(config, { accessToken: 'TEST-restored' })
      : response(config, { id: '42', name: 'TEST 旅行者' }))
    await expect(restored.session.restore()).resolves.toBe(true)
    expect(restored.members.at(-1)).toEqual({ id: '42', name: 'TEST 旅行者' })
    const anonymous = setup(unauthorized)
    await expect(anonymous.session.restore()).resolves.toBe(false)
    expect(anonymous.members.at(-1)).toEqual({})
    expect(anonymous.expired).toEqual([])
  })

  it('退出立即清空内存，并等待在途刷新后撤销最新 Cookie 会话', async () => {
    let finishRefresh
    let refreshStarted
    const started = new Promise((resolve) => { refreshStarted = resolve })
    const pending = new Promise((resolve) => { finishRefresh = resolve })
    const { api, session, members, requests, expired } = setup(async (config) => {
      if (config.url.endsWith('/login')) return response(config, { accessToken: 'TEST-old' })
      if (config.url.endsWith('/me')) return response(config, { id: '42', name: 'TEST 旅行者' })
      if (config.url.endsWith('/refresh')) { refreshStarted(); await pending; return response(config, { accessToken: 'TEST-new' }) }
      if (config.url.endsWith('/logout')) return response(config, null)
      return unauthorized(config)
    })
    await session.login({ email: 'test@example.com', password: 'TEST-password-123' })
    const requestResult = api.get('/lyw/web/private').catch(() => 'rejected')
    await started
    const logout = session.logout()
    expect(members.at(-1)).toEqual({})
    expect(requests.some((request) => request.url.endsWith('/logout'))).toBe(false)
    finishRefresh()
    await logout
    await expect(requestResult).resolves.toBe('rejected')
    expect(members.at(-1)).toEqual({})
    expect(requests.at(-1).url).toBe('/lyw/web/identity/logout')
    expect(expired).toEqual([])
  })
  it('刷新失败清空会员并仅通知一次，不递归重试', async () => {
    const { api, session, members, expired, requests } = setup(async (config) => {
      if (config.url.endsWith('/login')) return response(config, { accessToken: 'TEST-old' })
      if (config.url.endsWith('/me')) return response(config, { id: '42', name: 'TEST 旅行者' })
      return unauthorized(config)
    })
    await session.login({ email: 'test@example.com', password: 'TEST-password-123' })
    const results = await Promise.allSettled([api.get('/lyw/web/first'), api.get('/lyw/web/second')])
    expect(results.every((result) => result.status === 'rejected')).toBe(true)
    expect(members.at(-1)).toEqual({})
    expect(expired).toEqual([true])
    expect(requests.filter((request) => request.url.endsWith('/refresh'))).toHaveLength(1)
  })
  it('并发 401 共用一次 Cookie 刷新并重试各自请求', async () => {
    let refreshes = 0
    const { api, session, requests } = setup(async (config) => {
      if (config.url.endsWith('/login')) return response(config, { accessToken: 'TEST-old' })
      if (config.url.endsWith('/me')) return response(config, { id: '42', name: 'TEST 旅行者' })
      if (config.url.endsWith('/refresh')) { refreshes++; return response(config, { accessToken: 'TEST-new' }) }
      if (config.headers.get('Authorization') !== 'Bearer TEST-new') return unauthorized(config)
      return response(config, config.url)
    })
    await session.login({ email: 'test@example.com', password: 'TEST-password-123' })
    const results = await Promise.all([api.get('/lyw/web/first'), api.get('/lyw/web/second')])
    expect(results.map((result) => result.data.content)).toEqual(['/lyw/web/first', '/lyw/web/second'])
    expect(refreshes).toBe(1)
    expect(requests.filter((request) => request.url === '/lyw/web/first')).toHaveLength(2)
    expect(requests.filter((request) => request.url === '/lyw/web/second')).toHaveLength(2)
  })
  it('邮箱登录读取当前会员，访问凭据只用于本系统 Bearer 请求', async () => {
    const { api, session, members, requests } = setup(async (config) => {
      if (config.url.endsWith('/login')) return response(config, { accessToken: 'TEST-access' })
      if (config.url.endsWith('/me')) return response(config, { id: '42', name: 'TEST 旅行者' })
      return response(config, 'ok')
    })
    await session.login({ email: 'test@example.com', password: 'TEST-password-123' })
    await api.get('/lyw/web/member/heart')
    await api.get('https://other.example.com/lyw/web/private')
    expect(members.at(-1)).toEqual({ id: '42', name: 'TEST 旅行者' })
    expect(requests.at(-2).headers.get('Authorization')).toBe('Bearer TEST-access')
    expect(requests.at(-2).withCredentials).toBe(true)
    expect(requests.at(-1).headers.get('Authorization')).toBeUndefined()
    expect(requests.at(-1).withCredentials).not.toBe(true)
    expect(sessionStorage.getItem('member')).toBeNull()
    expect(localStorage.getItem('member')).toBeNull()
  })
})

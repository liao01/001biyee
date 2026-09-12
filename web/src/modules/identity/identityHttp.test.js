import axios from 'axios'
import { describe, expect, it } from 'vitest'
import { createIdentityHttp } from './identityHttp.js'

describe('邮箱身份 HTTP 边界', () => {
  it('注册、验证、刷新、退出和密码重置仅消费正式邮箱契约', async () => {
    const requests = []
    const transport = axios.create({ adapter: async (config) => {
      requests.push({ method: config.method, url: config.url, body: config.data ? JSON.parse(config.data) : undefined,
        authorization: config.headers.get('Authorization'), cookies: config.withCredentials })
      return { status: 200, data: { success: true, content: { id: '42', name: 'TEST 旅行者' } }, config }
    } })
    const identity = createIdentityHttp(transport)
    await identity.register({ email: 'test@example.com', password: 'TEST-password-123', confirmation: 'not-sent' })
    await identity.verifyEmail('TEST-verification')
    await identity.refresh()
    await identity.logout()
    await identity.requestPasswordReset('test@example.com')
    await identity.resetPassword('TEST-reset', 'TEST-new-password-123')
    await expect(identity.me('TEST-access')).resolves.toEqual({ id: '42', name: 'TEST 旅行者' })
    expect(requests.map(({ method, url, body }) => ({ method, url, body }))).toEqual([
      { method: 'post', url: '/lyw/web/identity/register', body: { email: 'test@example.com', password: 'TEST-password-123' } },
      { method: 'post', url: '/lyw/web/identity/verify-email', body: { token: 'TEST-verification' } },
      { method: 'post', url: '/lyw/web/identity/refresh', body: {} },
      { method: 'post', url: '/lyw/web/identity/logout', body: {} },
      { method: 'post', url: '/lyw/web/identity/request-password-reset', body: { email: 'test@example.com' } },
      { method: 'post', url: '/lyw/web/identity/reset-password', body: { token: 'TEST-reset', newPassword: 'TEST-new-password-123' } },
      { method: 'get', url: '/lyw/web/identity/me', body: undefined },
    ])
    expect(requests.every((request) => request.cookies)).toBe(true)
    expect(requests[6].authorization).toBe('Bearer TEST-access')
  })
  it('仅向正式邮箱登录接口发送原始密码，并允许安全 Cookie 往返', async () => {
    const requests = []
    const transport = axios.create({ adapter: async (config) => {
      requests.push(config)
      return { status: 200, data: { success: true, content: { accessToken: 'TEST-access' } }, config }
    } })
    const identity = createIdentityHttp(transport)
    await expect(identity.login({ email: 'test@example.com', password: 'TEST-password-123', mobile: 'obsolete' }))
      .resolves.toEqual({ accessToken: 'TEST-access' })
    expect(requests[0].url).toBe('/lyw/web/identity/login')
    expect(JSON.parse(requests[0].data)).toEqual({ email: 'test@example.com', password: 'TEST-password-123' })
    expect(requests[0].withCredentials).toBe(true)
  })
})

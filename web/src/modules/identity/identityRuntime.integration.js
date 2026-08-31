import axios from 'axios'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { expect, it, vi } from 'vitest'
import Register from '../../view/register.vue'
import Login from '../../view/login.vue'
import Forgot from '../../view/forgot.vue'
import { identityRoutes } from './identityRoutes.js'
import { identityKey } from './identityContext.js'
import { createIdentityHttp } from './identityHttp.js'
import { createIdentitySession } from './identitySession.js'

it('真实 Vue 表单通过隔离 HTTP 和 Cookie 完成邮箱身份全流程', async () => {
  const baseURL = process.env.LYW_IDENTITY_RUNTIME_BASE
  if (!baseURL || !/^http:\/\/127\.0\.0\.1:\d+$/.test(baseURL)) throw new Error('Requires an isolated loopback identity test server')
  const transport = axios.create({ baseURL, timeout: 10000, withCredentials: true })
  const identityHttp = createIdentityHttp(transport)
  let member = {}
  const createSession = () => createIdentitySession({ identity: identityHttp, apiBase: baseURL,
    onMember: (next) => { member = next }, onExpired: () => {} })
  const identitySession = createSession()
  const global = { provide: { [identityKey]: { identityHttp, identitySession } } }
  const wrappers = []
  const render = (component, options = {}) => { const wrapper = mount(component, { global, ...options }); wrappers.push(wrapper); return wrapper }
  const until = (assertion) => vi.waitFor(assertion, { timeout: 10000, interval: 30 })
  try {
    const register = render(Register)
    await register.get('input[type="email"]').setValue('vue-runtime@example.com')
    await register.get('input[name="password"]').setValue('Test-password-123')
    await register.get('input[name="confirmation"]').setValue('Test-password-123')
    await register.get('form').trigger('submit')
    await until(() => expect(register.get('[role="status"]').text()).toContain('检查邮箱'))
    register.unmount()
    const mailbox = await transport.get('/lyw/_test/identity-mailbox')
    const router = createRouter({ history: createMemoryHistory(), routes: [...identityRoutes,
      { path: '/CardList', component: { template: '<div>发现</div>' } }] })
    await router.push({ path: '/verify-email', query: { token: new URL(mailbox.data.verification).searchParams.get('token') } })
    const page = render({ template: '<router-view />' }, { global: { ...global, plugins: [router] } })
    await until(() => expect(page.get('[role="status"]').text()).toContain('邮箱验证成功'))
    expect(router.currentRoute.value.query.token).toBeUndefined()
    const login = render(Login)
    await login.get('input[type="email"]').setValue('vue-runtime@example.com')
    await login.get('input[type="password"]').setValue('Test-password-123')
    await login.get('form').trigger('submit')
    await until(() => expect(login.emitted('login-success')).toHaveLength(1))
    expect(member.id).toBeTruthy()
    const firstId = member.id
    const restartedSession = createSession()
    await expect(restartedSession.restore()).resolves.toBe(true)
    expect(member.id).toBe(firstId)
    await restartedSession.logout()
    await expect(createSession().restore()).resolves.toBe(false)
    expect(member).toEqual({})
    const forgot = render(Forgot)
    await forgot.get('input[type="email"]').setValue('vue-runtime@example.com')
    await forgot.get('form').trigger('submit')
    await until(() => expect(forgot.get('[role="status"]').text()).toContain('如果该邮箱可用于重置密码'))
    const resetMail = await transport.get('/lyw/_test/identity-mailbox')
    await router.push({ path: '/reset-password', query: { token: new URL(resetMail.data.reset).searchParams.get('token') } })
    await until(() => expect(page.find('input[name="password"]').exists()).toBe(true))
    await page.get('input[name="password"]').setValue('Test-new-password-456')
    await page.get('input[name="confirmation"]').setValue('Test-new-password-456')
    await page.get('form').trigger('submit')
    await until(() => expect(page.get('[role="status"]').text()).toContain('密码已更新'))
    await login.get('input[type="password"]').setValue('Test-new-password-456')
    await login.get('form').trigger('submit')
    await until(() => expect(login.emitted('login-success')).toHaveLength(2))
    expect(member.id).toBe(firstId)
    await identitySession.logout()
  } finally { for (const wrapper of wrappers) wrapper.unmount() }
})

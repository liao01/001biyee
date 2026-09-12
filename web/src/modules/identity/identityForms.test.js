import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import { describe, expect, it } from 'vitest'
import Login from '../../view/login.vue'
import Register from '../../view/register.vue'
import Forgot from '../../view/forgot.vue'
import { createIdentityHttp } from './identityHttp.js'
import { createIdentitySession } from './identitySession.js'
import { identityKey } from './identityContext.js'

function client(handler) {
  const requests = []
  const members = []
  const transport = axios.create({ adapter: async (config) => {
    requests.push(config)
    return { status: 200, data: await handler(config), config }
  } })
  const identityHttp = createIdentityHttp(transport)
  const identitySession = createIdentitySession({ identity: identityHttp, onMember: (member) => members.push(member), onExpired: () => {} })
  return { requests, members, global: { provide: { [identityKey]: { identityHttp, identitySession } } } }
}

describe('邮箱身份表单', () => {
  it('注册要求确认密码一致，成功后提示检查邮箱而不自动登录', async () => {
    const app = client(async () => ({ success: true }))
    const wrapper = mount(Register, { global: app.global })
    await wrapper.get('input[type="email"]').setValue('test@example.com')
    await wrapper.get('input[name="password"]').setValue('TEST-password-123')
    await wrapper.get('input[name="confirmation"]').setValue('TEST-mismatch')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="alert"]').text()).toContain('两次密码不一致')
    expect(app.requests).toHaveLength(0)
    await wrapper.get('input[name="confirmation"]').setValue('TEST-password-123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="status"]').text()).toContain('检查邮箱')
    expect(app.requests[0].url).toBe('/lyw/web/identity/register')
    expect(app.members).toEqual([])
    wrapper.unmount()
  })

  it('忘记密码只提交邮箱，显示统一结果', async () => {
    const app = client(async () => ({ success: true }))
    const wrapper = mount(Forgot, { global: app.global })
    expect(wrapper.findAll('input')).toHaveLength(1)
    await wrapper.get('input[type="email"]').setValue('test@example.com')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="status"]').text()).toContain('如果该邮箱可用于重置密码')
    expect(app.requests[0].url).toBe('/lyw/web/identity/request-password-reset')
    expect(JSON.parse(app.requests[0].data)).toEqual({ email: 'test@example.com' })
    wrapper.unmount()
  })
  it('登录只收集邮箱和密码，成功后显示真实当前会员并关闭弹窗', async () => {
    const app = client(async (config) => ({ success: true, content: config.url.endsWith('/me')
      ? { id: '42', name: 'TEST 旅行者' } : { accessToken: 'TEST-access' } }))
    const wrapper = mount(Login, { global: app.global })
    expect(wrapper.findAll('input')).toHaveLength(2)
    await wrapper.get('input[type="email"]').setValue('test@example.com')
    await wrapper.get('input[type="password"]').setValue('TEST-password-123')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(app.members.at(-1)).toEqual({ id: '42', name: 'TEST 旅行者' })
    expect(wrapper.emitted('login-success')).toHaveLength(1)
    expect(JSON.parse(app.requests[0].data)).toEqual({ email: 'test@example.com', password: 'TEST-password-123' })
    wrapper.unmount()
  })
})

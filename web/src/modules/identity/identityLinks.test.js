import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import axios from 'axios'
import { expect, it } from 'vitest'
import { createIdentityHttp } from './identityHttp.js'
import { identityKey } from './identityContext.js'
import { identityRoutes } from './identityRoutes.js'

async function openLink(path, handler) {
  const requests = []
  const identityHttp = createIdentityHttp(axios.create({ adapter: async (config) => {
    requests.push(config)
    return { status: 200, config, data: await handler(config) }
  } }))
  const router = createRouter({ history: createMemoryHistory(), routes: [...identityRoutes,
    { path: '/CardList', component: { template: '<div>发现</div>' } }] })
  await router.push(path)
  const wrapper = mount({ template: '<router-view />' }, { global: {
    plugins: [router], provide: { [identityKey]: { identityHttp } },
  } })
  await flushPromises()
  return { requests, router, wrapper }
}

it('邮件验证链接消费一次并从地址栏去除令牌', async () => {
  const { wrapper, router, requests } = await openLink('/verify-email?token=TEST-verification', async () => ({ success: true }))
  expect(router.currentRoute.value.query.token).toBeUndefined()
  expect(wrapper.get('[role="status"]').text()).toContain('邮箱验证成功')
  expect(requests).toHaveLength(1)
  expect(JSON.parse(requests[0].data)).toEqual({ token: 'TEST-verification' })
  wrapper.unmount()
})

it('密码重置链接只在两次新密码一致后提交', async () => {
  const { wrapper, router, requests } = await openLink('/reset-password?token=TEST-reset', async () => ({ success: true }))
  expect(router.currentRoute.value.query.token).toBeUndefined()
  expect(requests).toHaveLength(0)
  await wrapper.get('input[name="password"]').setValue('TEST-new-password-123')
  await wrapper.get('input[name="confirmation"]').setValue('TEST-other')
  await wrapper.get('form').trigger('submit')
  expect(wrapper.get('[role="alert"]').text()).toContain('两次密码不一致')
  expect(requests).toHaveLength(0)
  await wrapper.get('input[name="confirmation"]').setValue('TEST-new-password-123')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(wrapper.get('[role="status"]').text()).toContain('密码已更新')
  expect(JSON.parse(requests[0].data)).toEqual({ token: 'TEST-reset', newPassword: 'TEST-new-password-123' })
  wrapper.unmount()
})

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  routerPush: vi.fn(),
  loginShow: vi.fn(),
  member: {},
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/CardList' }),
  useRouter: () => ({ push: mocks.routerPush }),
}))
vi.mock('../store/index.js', () => ({
  default: { state: { member: mocks.member }, commit: vi.fn() },
}))
vi.mock('../store/search.js', () => ({
  useSearchStore: () => ({ setKeyword: vi.fn() }),
}))
vi.mock('../utils/baseUrl', () => ({ BASE_URL: 'http://example.invalid' }))
vi.mock('axios', () => ({ default: { get: vi.fn(() => Promise.resolve({ data: {} })) } }))
vi.mock('./the-login.vue', () => ({
  default: {
    template: '<div />',
    setup(_, { expose }) {
      expose({ showModal: mocks.loginShow })
    },
  },
}))

import TheHeader from './the-header.vue'

const LoginStub = {
  template: '<div />',
  setup(_, { expose }) {
    expose({ showModal: mocks.loginShow })
  },
}

describe('顶部发布入口', () => {
  it('未登录时打开现有登录弹窗而不是跳转发布页', async () => {
    const wrapper = mount(TheHeader, {
      global: {
        stubs: {
          TheLogin: LoginStub,
          'a-dropdown': { template: '<div><slot /><slot name="overlay" /></div>' },
          'a-menu': { template: '<div><slot /></div>' },
          'a-menu-item': { template: '<button><slot /></button>' },
          'a-menu-divider': true,
          'a-avatar': { template: '<div><slot /></div>' },
        },
      },
    })

    await wrapper.get('.travel-header__publish').trigger('click')

    expect(mocks.loginShow).toHaveBeenCalledOnce()
    expect(mocks.routerPush).not.toHaveBeenCalledWith('/uploadPost')
  })
})

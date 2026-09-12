import axios from 'axios'
import store from '../../store/index.js'
import { BASE_URL } from '../../utils/baseUrl.js'
import { createIdentityHttp } from './identityHttp.js'
import { createIdentitySession } from './identitySession.js'

// 独立传输不安装自动刷新拦截器，身份失败不会递归触发刷新。
export const identityHttp = createIdentityHttp(axios.create({ baseURL: BASE_URL, timeout: 10000 }))
export const identitySession = createIdentitySession({
  identity: identityHttp,
  apiBase: BASE_URL,
  onMember: (member) => store.commit('setMember', member),
  onExpired: () => window.showLogin?.(),
})

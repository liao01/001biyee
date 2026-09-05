import { inject } from 'vue'
import { identityHttp, identitySession } from './identityClient.js'

export const identityKey = Symbol('identity')
export const useIdentity = () => inject(identityKey, () => ({ identityHttp, identitySession }), true)

export const identityErrorMessage = (error) => error.response?.data?.message ||
  (error.response?.status === 401 ? '邮箱或密码错误，请重试' : '请求未成功，请检查网络后重试')

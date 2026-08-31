import { useRoute, useRouter } from 'vue-router'

export const useIdentityLink = () => {
  const route = useRoute()
  const router = useRouter()
  const token = typeof route.query.token === 'string' ? route.query.token : ''
  if ('token' in route.query) {
    const query = { ...route.query }
    delete query.token
    // 只保留在组件内存中；刷新页面需要从原邮件重新打开。
    router.replace({ path: route.path, query, hash: route.hash }).catch(() => {})
  }
  return token
}

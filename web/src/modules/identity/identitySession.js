import { buildApiUrl } from '../../utils/baseUrl.js'

export function createIdentitySession({ identity, onMember, onExpired, apiBase = '', origin = window.location.origin }) {
  let accessToken = null
  let refreshPromise = null
  let tokenVersion = 0
  let generation = 0
  let logoutPromise = null
  let refreshAllowed = true
  let expiredNotified = false
  const notifyExpired = () => { if (!expiredNotified) { expiredNotified = true; onExpired() } }
  const apiRoot = new URL(buildApiUrl('/lyw/web/', apiBase), origin)
  const isApiRequest = (http, config) => {
    const url = new URL(http.getUri(config), origin)
    return url.origin === apiRoot.origin && url.pathname.startsWith(apiRoot.pathname)
  }
  const acceptTokens = async (tokens, expectedGeneration) => {
    if (generation !== expectedGeneration) throw new Error('会话操作已取消')
    const member = await identity.me(tokens.accessToken)
    if (generation !== expectedGeneration) throw new Error('会话操作已取消')
    accessToken = tokens.accessToken
    expiredNotified = false
    tokenVersion++
    onMember({ id: member.id, name: member.name })
  }
  const clear = () => { generation++; accessToken = null; tokenVersion++; onMember({}) }
  const refresh = (notify = true) => {
    if (!refreshPromise) {
      const expectedGeneration = generation
      refreshPromise = identity.refresh().then((tokens) => acceptTokens(tokens, expectedGeneration)).catch((error) => {
        if (generation === expectedGeneration) {
          clear()
          refreshAllowed = false
          if (notify) notifyExpired()
        }
        throw error
      }).finally(() => { refreshPromise = null })
    }
    return refreshPromise
  }
  return {
    async restore() {
      try { await refresh(false); return true } catch { return false }
    },
    async login(credentials) {
      if (logoutPromise) await logoutPromise
      clear()
      refreshAllowed = true
      const expectedGeneration = generation
      await acceptTokens(await identity.login(credentials), expectedGeneration)
    },
    logout() {
      if (logoutPromise) return logoutPromise
      clear()
      refreshAllowed = false
      logoutPromise = Promise.resolve(refreshPromise).catch(() => {}).then(() => identity.logout())
        .finally(() => { logoutPromise = null })
      return logoutPromise
    },
    install(http) {
      const requestId = http.interceptors.request.use((config) => {
        if (isApiRequest(http, config)) {
          config.withCredentials = true
          config.headers.delete('token')
          config.identityTokenVersion = tokenVersion
          config.identityGeneration = generation
          if (accessToken) config.headers.set('Authorization', `Bearer ${accessToken}`)
          else config.headers.delete('Authorization')
        }
        return config
      })
      const responseId = http.interceptors.response.use((response) => response, async (error) => {
        const config = error.config
        if (error.response?.status !== 401 || !config || !isApiRequest(http, config)
          || config.identityGeneration !== generation) throw error
        if (!refreshAllowed) { notifyExpired(); throw error }
        if (config.identityRetried) {
          clear()
          refreshAllowed = false
          notifyExpired()
          throw error
        }
        const expectedGeneration = generation
        config.identityRetried = true
        if (!accessToken || config.identityTokenVersion === tokenVersion) await refresh()
        if (generation !== expectedGeneration || !accessToken) throw error
        return http.request(config)
      })
      return () => {
        http.interceptors.request.eject(requestId)
        http.interceptors.response.eject(responseId)
      }
    },
  }
}

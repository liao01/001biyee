const AMAP_LOADER_URL = 'https://webapi.amap.com/loader.js'
const AMAP_SERVICE_HOST = '/_AMapService/'

let activePromise = null
let activeKey = null
let loadedKey = null
let injectedScript = null

const configuredKey = () => {
  const runtimeKey = window.__LYW_RUNTIME_CONFIG__?.amapJsKey
  const key = runtimeKey || import.meta.env.VITE_AMAP_JS_KEY
  return typeof key === 'string' ? key.trim() : ''
}

const developmentSecurityCode = () => {
  if (!import.meta.env.DEV) return ''
  const value = import.meta.env.VITE_AMAP_JS_SECURITY_CODE
  return typeof value === 'string' ? value.trim() : ''
}

const loadOfficialLoader = () => new Promise((resolve, reject) => {
  if (window.AMapLoader) {
    resolve(window.AMapLoader)
    return
  }
  const script = document.createElement('script')
  script.async = true
  script.src = AMAP_LOADER_URL
  script.onload = () => window.AMapLoader ? resolve(window.AMapLoader) : reject(new Error('高德地图加载器不可用'))
  script.onerror = () => reject(new Error('高德地图脚本加载失败'))
  injectedScript = script
  document.head.append(script)
})

const releaseAfterFailure = (error) => {
  injectedScript?.remove()
  injectedScript = null
  activePromise = null
  activeKey = null
  throw error
}

export const loadAmap = () => {
  const key = configuredKey()
  if (!key) return Promise.reject(new Error('未配置高德地图 JavaScript 密钥'))
  if (loadedKey && loadedKey !== key) return Promise.reject(new Error('运行时地图密钥已变更，请刷新页面'))
  if (activePromise) {
    return activeKey === key
      ? activePromise
      : Promise.reject(new Error('运行时地图密钥已变更，请刷新页面'))
  }

  activeKey = key
  const securityJsCode = developmentSecurityCode()
  window._AMapSecurityConfig = securityJsCode ? { securityJsCode } : { serviceHost: AMAP_SERVICE_HOST }
  activePromise = loadOfficialLoader()
    .then((loader) => loader.load({ key, version: '2.0', plugins: ['AMap.ToolBar'] }))
    .then((amap) => {
      loadedKey = key
      return amap
    })
    .catch(releaseAfterFailure)
  return activePromise
}

export const resetAmapLoaderForTests = () => {
  activePromise = null
  activeKey = null
  loadedKey = null
  injectedScript = null
}

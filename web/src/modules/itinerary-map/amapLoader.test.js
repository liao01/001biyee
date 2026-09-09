import { afterEach, describe, expect, it, vi } from 'vitest'

import { loadAmap, resetAmapLoaderForTests } from './amapLoader.js'

const scriptSelector = 'script[src*="webapi.amap.com/loader.js"]'

afterEach(() => {
  document.querySelector(scriptSelector)?.remove()
  delete window.AMap
  delete window.AMapLoader
  delete window._AMapSecurityConfig
  delete window.__LYW_RUNTIME_CONFIG__
  vi.unstubAllEnvs()
  resetAmapLoaderForTests()
})

describe('高德 JS Loader', () => {
  it('同一运行时配置只加载一次，并固定同源安全服务与 2.0 ToolBar', async () => {
    window.__LYW_RUNTIME_CONFIG__ = { amapJsKey: 'TEST-js-key' }
    const load = vi.fn().mockResolvedValue({ Map: vi.fn(), Marker: vi.fn(), Polyline: vi.fn() })
    const first = loadAmap()
    const second = loadAmap()

    expect(first).toBe(second)
    const script = document.querySelector(scriptSelector)
    expect(script).not.toBeNull()
    expect(window._AMapSecurityConfig).toEqual({ serviceHost: '/_AMapService/' })
    window.AMapLoader = { load }
    script.dispatchEvent(new Event('load'))

    await expect(first).resolves.toMatchObject({ Map: expect.any(Function) })
    expect(load).toHaveBeenCalledWith({ key: 'TEST-js-key', version: '2.0', plugins: ['AMap.ToolBar'] })
    expect(document.querySelectorAll(scriptSelector)).toHaveLength(1)
  })

  it('加载失败会允许用户重试；已经加载后拒绝不同的密钥配置', async () => {
    window.__LYW_RUNTIME_CONFIG__ = { amapJsKey: 'TEST-js-key' }
    const first = loadAmap()
    const failedScript = document.querySelector(scriptSelector)
    failedScript.dispatchEvent(new Event('error'))
    await expect(first).rejects.toThrow('高德地图脚本加载失败')

    const retry = loadAmap()
    const retryScript = document.querySelector(scriptSelector)
    window.AMapLoader = { load: vi.fn().mockResolvedValue({ Map: vi.fn() }) }
    retryScript.dispatchEvent(new Event('load'))
    await retry

    window.__LYW_RUNTIME_CONFIG__ = { amapJsKey: 'ANOTHER-js-key' }
    await expect(loadAmap()).rejects.toThrow('运行时地图密钥已变更')
  })

  it('开发环境允许使用明确的本机安全码，而生产运行时保持同源代理配置', () => {
    vi.stubEnv('VITE_AMAP_JS_SECURITY_CODE', 'local-security-code')
    window.__LYW_RUNTIME_CONFIG__ = { amapJsKey: 'TEST-js-key' }
    void loadAmap()

    expect(window._AMapSecurityConfig).toEqual({ securityJsCode: 'local-security-code' })
  })
})

import { describe, expect, it } from 'vitest'
import axios from 'axios'
import { buildApiUrl, configureGlobalAxios, normalizeBaseUrl } from './baseUrl.js'

describe('deployment API base', () => {
  it('normalizes the configured prefix and joins legacy routes', () => {
    expect(normalizeBaseUrl('/business/')).toBe('/business')
    expect(buildApiUrl('/lyw/web/post/categories', '/business/'))
      .toBe('/business/lyw/web/post/categories')
  })

  it('keeps an explicitly built API URL from receiving the prefix twice', () => {
    const client = axios.create({ baseURL: '/business' })

    configureGlobalAxios(client)

    expect(client.getUri({ url: buildApiUrl('/lyw/web/post/categories', '/business') }))
      .toBe('/business/lyw/web/post/categories')
  })
})

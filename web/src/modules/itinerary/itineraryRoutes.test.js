import { describe, expect, it } from 'vitest'

import { itineraryRoutes } from './itineraryRoutes.js'

describe('行程路由事实源', () => {
  it('声明列表、创建和可深链编辑器路由', () => {
    expect(itineraryRoutes.map((route) => route.path)).toEqual([
      'itineraries', 'itineraries/new', 'itineraries/:itineraryId',
    ])
    expect(itineraryRoutes.every((route) => route.meta.requiresAuth)).toBe(true)
  })
})

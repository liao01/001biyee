import { describe, expect, it } from 'vitest'

import { formatDate, formatDateRange, formatMoney } from './itineraryFormatters.js'

describe('行程格式化', () => {
  it('日期只按日历日期格式化，不受浏览器本地时区影响', () => {
    expect(formatDate('2026-09-01', 'zh-CN')).toBe('2026年9月1日')
    expect(formatDateRange('2026-09-01', '2026-09-03', 'zh-CN')).toBe('2026年9月1日—2026年9月3日')
  })

  it('金额由行程基准币种决定并保留两位小数', () => {
    expect(formatMoney('28', 'CNY', 'zh-CN')).toContain('28.00')
    expect(formatMoney(null, 'CNY', 'zh-CN')).toBe('')
  })
})

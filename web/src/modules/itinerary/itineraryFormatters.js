const calendarDate = (value) => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '')) return null
  const date = new Date(`${value}T12:00:00.000Z`)
  return Number.isNaN(date.getTime()) ? null : date
}

export const formatDate = (value, locale = 'zh-CN') => {
  const date = calendarDate(value)
  if (!date) return ''
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric', month: 'long', day: 'numeric', timeZone: 'UTC',
  }).format(date)
}

export const formatDateRange = (startDate, endDate, locale = 'zh-CN') => {
  const start = formatDate(startDate, locale)
  const end = formatDate(endDate, locale)
  if (!start || !end) return start || end
  return start === end ? start : `${start}—${end}`
}

export const formatMoney = (value, currency, locale = 'zh-CN') => {
  if (value == null || value === '' || !currency) return ''
  const amount = Number(value)
  if (!Number.isFinite(amount)) return ''
  return new Intl.NumberFormat(locale, {
    style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2,
  }).format(amount)
}

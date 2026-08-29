export const fetchPostCategories = async (http, baseUrl = '') => {
  const { data } = await http.get(`${baseUrl}/web/post/categories`)
  if (!data.success) {
    throw new Error(data.message || '分类加载失败')
  }
  return Array.isArray(data.content) ? data.content : []
}

export const buildDiscoveryParams = ({ view, categoryCode } = {}) => {
  if (categoryCode) return { categoryCode }
  if (view === 'LATEST') return { view: 'LATEST' }
  return {}
}

export const normalizeBaseUrl = (value = '') => {
  const normalized = value.trim().replace(/\/+$/, '')
  return normalized === '/' ? '' : normalized
}

export const buildApiUrl = (path, base = import.meta.env.VITE_BASE_URL) => {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${normalizeBaseUrl(base)}${normalizedPath}`
}

export const configureGlobalAxios = (http) => {
  delete http.defaults.baseURL
  return http
}

export const BASE_URL = normalizeBaseUrl(import.meta.env.VITE_BASE_URL)

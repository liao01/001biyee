import request from '../../utils/request.js'

const ROOT = '/lyw/web/itineraries'

export class ItineraryHttpError extends Error {
  constructor(message, { status, errorCode, cause } = {}) {
    super(message, { cause })
    this.name = 'ItineraryHttpError'
    this.status = status
    this.errorCode = errorCode
  }
}

const normalizeIds = (value, key = '') => {
  if (value == null) return value
  if (Array.isArray(value)) {
    return value.map((entry) => normalizeIds(entry, key === 'itemIds' ? 'id' : ''))
  }
  if (typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([childKey, child]) => [
      childKey, normalizeIds(child, childKey),
    ]))
  }
  if ((key === 'id' || key.endsWith('Id')) && (typeof value === 'number' || typeof value === 'bigint')) {
    return String(value)
  }
  return value
}

const read = async (operation) => {
  try {
    const { data } = await operation
    if (!data.success) {
      throw new ItineraryHttpError(data.message || '行程请求未成功', {
        errorCode: data.content?.errorCode,
      })
    }
    return normalizeIds(data.content)
  } catch (error) {
    if (error instanceof ItineraryHttpError) throw error
    const response = error.response
    throw new ItineraryHttpError(
      response?.data?.message || error.message || '行程请求失败',
      {
        status: response?.status,
        errorCode: response?.data?.content?.errorCode,
        cause: error,
      },
    )
  }
}

export const createItineraryHttp = (http = request) => {
  const send = (method, url, data, params) => read(http.request({
    method,
    url,
    ...(data === undefined ? {} : { data }),
    ...(params === undefined ? {} : { params }),
  }))
  return {
    list: (params = {}) => send('get', ROOT, undefined, {
      ...params,
      status: Array.isArray(params.status) ? params.status.join(',') : params.status,
    }),
    create: (command) => send('post', ROOT, command),
    get: (itineraryId) => send('get', `${ROOT}/${itineraryId}`),
    updateOverview: (itineraryId, command) => send('patch', `${ROOT}/${itineraryId}`, command),
    replaceDestinations: (itineraryId, command) => send('put', `${ROOT}/${itineraryId}/destinations`, command),
    addItem: (itineraryId, command) => send('post', `${ROOT}/${itineraryId}/items`, command),
    updateItem: (itineraryId, itemId, command) => send('patch', `${ROOT}/${itineraryId}/items/${itemId}`, command),
    deleteItem: (itineraryId, itemId, command) => send('delete', `${ROOT}/${itineraryId}/items/${itemId}`, command),
    reorderItems: (itineraryId, dayId, command) => send('put', `${ROOT}/${itineraryId}/days/${dayId}/item-order`, command),
    transition: (itineraryId, command) => send('post', `${ROOT}/${itineraryId}/status-transitions`, command),
  }
}

export const itineraryHttp = createItineraryHttp()

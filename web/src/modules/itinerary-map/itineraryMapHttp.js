import request from '../../utils/request.js'
import { normalizeIds } from '../itinerary/itineraryHttp.js'

const ROOT = '/lyw/web'

export class ItineraryMapHttpError extends Error {
  constructor(message, { status, errorCode, cause } = {}) {
    super(message, { cause })
    this.name = 'ItineraryMapHttpError'
    this.status = status
    this.errorCode = errorCode
  }
}

export const itineraryMapHttpKey = Symbol('itineraryMapHttp')

const read = async (operation) => {
  try {
    const { data } = await operation
    if (!data.success) {
      throw new ItineraryMapHttpError(data.message || '行程地点请求未成功', {
        errorCode: data.content?.errorCode,
      })
    }
    return normalizeIds(data.content)
  } catch (error) {
    if (error instanceof ItineraryMapHttpError) throw error
    const response = error.response
    throw new ItineraryMapHttpError(
      response?.data?.message || error.message || '行程地点请求失败',
      {
        status: response?.status,
        errorCode: response?.data?.content?.errorCode,
        cause: error,
      },
    )
  }
}

export const createItineraryMapHttp = (http = request) => {
  const send = (method, url, data, params) => read(http.request({
    method,
    url,
    ...(data === undefined ? {} : { data }),
    ...(params === undefined ? {} : { params }),
  }))
  return {
    searchPlaces: ({ keyword, city, limit = 10 }) => send('get', `${ROOT}/places/search`, undefined, { keyword, city, limit }),
    putPlace: (itineraryId, itemId, command) => send('put', `${ROOT}/itineraries/${itineraryId}/items/${itemId}/place`, command),
    removePlace: (itineraryId, itemId, command) => send('delete', `${ROOT}/itineraries/${itineraryId}/items/${itemId}/place`, command),
    dailyMap: (itineraryId, dayId, mode = 'WALKING') => send('get', `${ROOT}/itineraries/${itineraryId}/days/${dayId}/map`, undefined, { mode }),
  }
}

export const itineraryMapHttp = createItineraryMapHttp()

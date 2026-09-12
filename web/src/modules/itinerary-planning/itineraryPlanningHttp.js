import request from '../../utils/request.js'

export class ItineraryPlanningHttpError extends Error {
  constructor(message, { status, errorCode } = {}) {
    super(message)
    this.name = 'ItineraryPlanningHttpError'
    this.status = status
    this.errorCode = errorCode
  }
}

export const itineraryPlanningHttpKey = Symbol('itineraryPlanningHttp')

const normalizeIds = (value, key = '') => {
  if (value == null) return value
  if (Array.isArray(value)) return value.map((entry) => normalizeIds(entry))
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
    if (!data?.success) {
      throw new ItineraryPlanningHttpError(data?.message || 'AI 行程规划请求未成功', {
        errorCode: data?.content?.errorCode,
      })
    }
    return normalizeIds(data.content)
  } catch (error) {
    if (error instanceof ItineraryPlanningHttpError) throw error
    const response = error.response
    throw new ItineraryPlanningHttpError(
      response?.data?.message || 'AI 行程规划请求失败',
      {
        status: response?.status,
        errorCode: response?.data?.content?.errorCode,
      },
    )
  }
}

export const createItineraryPlanningHttp = (http = request) => {
  const root = (itineraryId) => `/lyw/web/itineraries/${itineraryId}/planning`
  const send = (method, url, data) => read(http.request({
    method,
    url,
    ...(data === undefined ? {} : { data }),
  }))
  return {
    getRequest: (itineraryId) => send('get', `${root(itineraryId)}/request`),
    saveRequest: (itineraryId, command) => send('put', `${root(itineraryId)}/request`, command),
    generate: (itineraryId, command) => send('post', `${root(itineraryId)}/generate`, command),
    listProposals: (itineraryId) => send('get', `${root(itineraryId)}/proposals`),
    getProposal: (itineraryId, proposalId) => send(
      'get', `${root(itineraryId)}/proposals/${proposalId}`,
    ),
    confirm: (itineraryId, proposalId, command) => send(
      'post', `${root(itineraryId)}/proposals/${proposalId}/confirm`, command,
    ),
    reject: (itineraryId, proposalId, command) => send(
      'post', `${root(itineraryId)}/proposals/${proposalId}/reject`, command,
    ),
  }
}

export const itineraryPlanningHttp = createItineraryPlanningHttp()

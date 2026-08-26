import axios from 'axios'

import { BASE_URL } from '../../utils/baseUrl.js'

export const postDetailHttpKey = Symbol('postDetailHttp')
const serverBaseUrl = (BASE_URL || '').replace(/\/$/, '')

const readContent = async (request) => {
  const { data } = await request
  if (!data.success) {
    throw new Error(data.message || '帖子详情请求失败')
  }
  return data.content
}

const assetUrl = (url) => {
  if (!url || /^(?:https?:|data:|blob:)/.test(url)) return url
  if (url.startsWith('/lyw/')) return `${serverBaseUrl}${url}`
  return `${serverBaseUrl}/lyw${url.startsWith('/') ? url : `/${url}`}`
}

const normalizeComment = (comment) => ({
  ...comment,
  avatar: assetUrl(comment.avatar),
})

const normalizePublicDetail = (detail) => ({
  ...detail,
  author: {
    ...detail.author,
    avatar: assetUrl(detail.author?.avatar),
  },
  images: (detail.images || []).map(assetUrl),
  comments: (detail.comments || []).map(normalizeComment),
})

export const createPostDetailHttp = (http = axios) => ({
  async getPublicDetail(postId) {
    const detail = await readContent(http.get(`${serverBaseUrl}/lyw/web/post/detail`, {
      params: { postId },
    }))
    return normalizePublicDetail(detail)
  },

  getViewerState(postId) {
    return readContent(http.get(`${serverBaseUrl}/lyw/web/post/detail/viewer-state`, {
      params: { postId },
    }))
  },

  setLike(postId, active) {
    return readContent(http.post(`${serverBaseUrl}/lyw/web/post/detail/like`, { postId, active }))
  },

  setFavorite(postId, active) {
    return readContent(http.post(`${serverBaseUrl}/lyw/web/post/detail/favorite`, { postId, active }))
  },

  setFollow(postId, active) {
    return readContent(http.post(`${serverBaseUrl}/lyw/web/post/detail/follow`, { postId, active }))
  },

  async createComment(postId, content) {
    const comment = await readContent(http.post(`${serverBaseUrl}/lyw/web/comment/save-comment`, {
      postId,
      content,
    }))
    return normalizeComment(comment)
  },

  async updateComment(id, content) {
    const comment = await readContent(http.post(`${serverBaseUrl}/lyw/web/comment/update-comment`, {
      id,
      content,
    }))
    return normalizeComment(comment)
  },

  deleteComment(id) {
    return readContent(http.post(`${serverBaseUrl}/lyw/web/comment/del-comment`, { id }))
  },

  recordView(postId) {
    return readContent(http.post(`${serverBaseUrl}/lyw/web/postview/save`, { postId }))
  },
})

export const postDetailHttp = createPostDetailHttp()

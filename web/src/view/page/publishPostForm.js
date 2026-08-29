export const validatePostDraft = ({ title, content, categoryCode, images }) => {
  if (!title?.trim()) return '请填写标题'
  if (!content?.trim()) return '请填写旅行正文'
  if (!categoryCode) return '请选择内容分类'
  if (!images?.length) return '请至少添加一张旅行图片'
  return ''
}

export const buildPostPayload = ({ title, content, categoryCode, images }) => ({
  title: title.trim(),
  content: content.trim(),
  categoryCode,
  images,
})

export const toPostPreview = (post, { baseUrl, maxDescriptionLength = 70 }) => {
  const description = post.postContent || ''

  return {
    id: post.postId,
    raw: post,
    image: baseUrl + (post.imageUrls?.split(',')[0] || ''),
    title: post.postTitle,
    description: description.length > maxDescriptionLength
      ? description.substring(0, maxDescriptionLength) + '...'
      : description,
    author: post.postMembername,
    categoryCode: post.categoryCode || '',
    categoryName: post.categoryName || '',
    publishedAt: post.postTime,
  }
}

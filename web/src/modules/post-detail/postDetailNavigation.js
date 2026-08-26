export const postDetailNavigationKey = Symbol('postDetailNavigation')

export const createPostDetailNavigation = (router) => ({
  openAuthor(authorId) {
    return router.push({
      name: 'author-detail',
      params: { authorId: String(authorId) },
    })
  },
})

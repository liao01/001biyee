<template>
  <div
    v-if="open"
    class="post-detail-backdrop"
    @click.self="emit('update:open', false)"
  >
    <section
      class="post-detail"
      role="dialog"
      aria-modal="true"
      aria-labelledby="post-detail-title"
    >
      <button
        class="post-detail__close"
        type="button"
        aria-label="关闭帖子详情"
        @click="emit('update:open', false)"
      >
        <CloseOutlined />
      </button>

      <div v-if="loading" class="post-detail__state">加载中…</div>

      <div v-else-if="errorMessage" class="post-detail__state" role="alert">
        <p>{{ errorMessage }}</p>
        <button class="travel-primary-button" type="button" aria-label="重试帖子详情" @click="loadDetail">重试</button>
      </div>

      <article v-else-if="detail" class="post-detail__layout">
        <main class="post-detail__reader">
          <header class="post-detail__heading">
            <span class="post-detail__back-label">旅行正文</span>
            <h2 id="post-detail-title">{{ detail.post.title }}</h2>

            <div class="post-detail__byline">
              <button
                type="button"
                class="post-detail__author"
                :aria-label="`查看作者 ${detail.author.name}`"
                @click="navigation.openAuthor(detail.author.id)"
              >
                <img
                  v-if="detail.author.avatar"
                  :src="detail.author.avatar"
                  :alt="`${detail.author.name}的头像`"
                >
                <span>{{ detail.author.name }}</span>
              </button>
              <time v-if="detail.post.postTime" :datetime="detail.post.postTime">
                {{ formatDisplayDate(detail.post.postTime) }}
              </time>
            </div>
          </header>

          <div v-if="detail.images?.length" class="post-detail__images">
            <img
              v-for="(image, index) in detail.images"
              :key="image"
              :src="image"
              :alt="`${detail.post.title} 图片 ${index + 1}`"
            >
          </div>

          <div class="post-detail__copy">
            <h3>旅行正文</h3>
            <p>{{ detail.post.description }}</p>
          </div>

          <section class="post-detail__comments" aria-labelledby="post-detail-comments-title">
            <div class="post-detail__section-heading">
              <h3 id="post-detail-comments-title">评论</h3>
              <span>{{ detail.comments.length }} 条讨论</span>
            </div>
            <div v-if="viewerState" class="post-detail__comment-form">
              <textarea v-model="commentDraft" aria-label="添加评论内容" placeholder="说说你的旅行感受…" />
              <button
                class="travel-primary-button"
                type="button"
                aria-label="发布评论"
                :disabled="commentPending || !commentDraft.trim()"
                @click="handleCreateComment"
              >发布</button>
            </div>
            <p v-if="commentMessage" role="alert" aria-label="评论操作反馈">{{ commentMessage }}</p>
            <ul class="post-detail__comment-list">
              <li v-for="comment in detail.comments" :key="comment.id" :data-comment-id="comment.id">
                <img v-if="comment.avatar" :src="comment.avatar" :alt="`${comment.membername}的头像`">
                <div class="post-detail__comment-content">
                  <div class="post-detail__comment-meta">
                    <strong>{{ comment.membername }}</strong>
                    <small>#{{ comment.id }}</small>
                    <time v-if="comment.commentTime" :datetime="comment.commentTime">{{ comment.commentTime }}</time>
                  </div>
                  <span>{{ comment.commentContent }}</span>
                  <div v-if="editingCommentId === String(comment.id)" class="post-detail__comment-edit">
                    <textarea v-model="editCommentDraft" :aria-label="`编辑评论内容 ${comment.id}`" />
                    <button type="button" :aria-label="`保存评论 ${comment.id}`" :disabled="commentPendingId !== null || !editCommentDraft.trim()" @click="handleUpdateComment(comment)">保存</button>
                    <button type="button" :aria-label="`取消编辑评论 ${comment.id}`" :disabled="commentPendingId !== null" @click="cancelCommentEdit">取消</button>
                  </div>
                  <div v-if="ownsComment(comment)" class="post-detail__comment-actions">
                    <button v-if="editingCommentId !== String(comment.id)" type="button" :aria-label="`编辑评论 ${comment.id}`" :disabled="commentPendingId !== null" @click="startCommentEdit(comment)">编辑</button>
                    <button type="button" :aria-label="`删除评论 ${comment.id}`" :disabled="commentPendingId !== null" @click="handleDeleteComment(comment)">删除</button>
                  </div>
                </div>
              </li>
            </ul>
          </section>
        </main>

        <aside class="post-detail__aside">
          <div class="post-detail__author-card">
            <button type="button" class="post-detail__author post-detail__author--large" :aria-label="`查看作者 ${detail.author.name}`" @click="navigation.openAuthor(detail.author.id)">
              <img v-if="detail.author.avatar" :src="detail.author.avatar" :alt="`${detail.author.name}的头像`">
              <span>{{ detail.author.name }}</span>
            </button>
            <button
              v-if="!viewerState?.selfAuthor"
              class="post-detail__follow"
              type="button"
              aria-label="关注作者"
              :aria-pressed="viewerState?.followed ?? false"
              :disabled="followPending"
              @click="handleFollow"
            >{{ viewerState?.followed ? '已关注' : '关注' }}</button>
          </div>

          <div class="post-detail__interactions">
            <button type="button" aria-label="点赞" :aria-pressed="viewerState?.liked ?? false" :disabled="likePending" @click="handleLike">
              <HeartOutlined />
              <span>点赞</span>
              <strong>{{ detail.interactionCounts.like }}</strong>
            </button>
            <button type="button" aria-label="收藏" :aria-pressed="viewerState?.favorited ?? false" :disabled="favoritePending" @click="handleFavorite">
              <StarOutlined />
              <span>收藏</span>
              <strong>{{ detail.interactionCounts.favorite }}</strong>
            </button>
          </div>
          <p v-if="viewerStateUnavailable || interactionMessage" class="post-detail__status" role="status">
            {{ interactionMessage || '登录后可查看个人互动状态' }}
          </p>
        </aside>
      </article>
    </section>
  </div>
</template>

<script setup>
import { inject, ref, watch } from 'vue'
import { CloseOutlined, HeartOutlined, StarOutlined } from '@ant-design/icons-vue'

import { postDetailHttp, postDetailHttpKey } from './postDetailHttp.js'
import { postDetailNavigationKey } from './postDetailNavigation.js'

const props = defineProps({
  open: {
    type: Boolean,
    required: true,
  },
  postId: {
    type: [String, Number],
    default: null,
  },
})
const emit = defineEmits(['update:open'])

const http = inject(postDetailHttpKey, postDetailHttp)
const navigation = inject(postDetailNavigationKey, { openAuthor: () => {} })
const detail = ref(null)
const viewerState = ref(null)
const viewerStateUnavailable = ref(false)
const interactionMessage = ref('')
const likePending = ref(false)
const favoritePending = ref(false)
const followPending = ref(false)
const commentDraft = ref('')
const commentPending = ref(false)
const commentMessage = ref('')
const editingCommentId = ref(null)
const editCommentDraft = ref('')
const commentPendingId = ref(null)
const loading = ref(false)
const errorMessage = ref('')
let requestVersion = 0

const formatDisplayDate = (value) => {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}

const resetPostState = () => {
  loading.value = false
  errorMessage.value = ''
  detail.value = null
  viewerState.value = null
  viewerStateUnavailable.value = false
  interactionMessage.value = ''
  likePending.value = false
  favoritePending.value = false
  followPending.value = false
  commentDraft.value = ''
  commentPending.value = false
  commentMessage.value = ''
  editingCommentId.value = null
  editCommentDraft.value = ''
  commentPendingId.value = null
}

const requestLogin = () => {
  interactionMessage.value = '请先登录后再互动'
  window.showLogin?.()
}

const runViewerMutation = async ({ pending, currentActive, write, apply, failureMessage }) => {
  if (!viewerState.value) {
    requestLogin()
    return
  }
  if (pending.value) return

  const currentRequestVersion = requestVersion
  pending.value = true
  interactionMessage.value = ''
  try {
    const result = await write(!currentActive())
    if (currentRequestVersion !== requestVersion) return
    apply(result)
  } catch {
    if (currentRequestVersion === requestVersion) {
      interactionMessage.value = failureMessage
    }
  } finally {
    if (currentRequestVersion === requestVersion) pending.value = false
  }
}

const handleLike = () => runViewerMutation({
  pending: likePending,
  currentActive: () => viewerState.value.liked,
  write: (active) => http.setLike(props.postId, active),
  apply: (result) => {
    viewerState.value.liked = result.active
    detail.value.interactionCounts.like = result.count
  },
  failureMessage: '点赞失败，请重试',
})

const handleFavorite = () => runViewerMutation({
  pending: favoritePending,
  currentActive: () => viewerState.value.favorited,
  write: (active) => http.setFavorite(props.postId, active),
  apply: (result) => {
    viewerState.value.favorited = result.active
    detail.value.interactionCounts.favorite = result.count
  },
  failureMessage: '收藏失败，请重试',
})

const handleFollow = () => {
  if (viewerState.value?.selfAuthor) return
  return runViewerMutation({
    pending: followPending,
    currentActive: () => viewerState.value.followed,
    write: (active) => http.setFollow(props.postId, active),
    apply: (result) => {
      viewerState.value.followed = result.followed
    },
    failureMessage: '关注失败，请重试',
  })
}

const handleCreateComment = async () => {
  const content = commentDraft.value.trim()
  if (!viewerState.value || !content || commentPending.value) return

  const currentRequestVersion = requestVersion
  commentPending.value = true
  commentMessage.value = ''
  try {
    const comment = await http.createComment(props.postId, content)
    if (currentRequestVersion !== requestVersion) return
    detail.value.comments.push(comment)
    commentDraft.value = ''
  } catch {
    if (currentRequestVersion === requestVersion) {
      commentMessage.value = '评论发布失败，请重试'
    }
  } finally {
    if (currentRequestVersion === requestVersion) commentPending.value = false
  }
}

const ownsComment = (comment) => viewerState.value
  && String(comment.userId) === String(viewerState.value.viewerId)

const startCommentEdit = (comment) => {
  editingCommentId.value = String(comment.id)
  editCommentDraft.value = comment.commentContent
  commentMessage.value = ''
}

const cancelCommentEdit = () => {
  editingCommentId.value = null
  editCommentDraft.value = ''
}

const handleUpdateComment = async (comment) => {
  const content = editCommentDraft.value.trim()
  if (!ownsComment(comment) || !content || commentPendingId.value !== null) return

  const currentRequestVersion = requestVersion
  commentPendingId.value = String(comment.id)
  commentMessage.value = ''
  try {
    const updated = await http.updateComment(comment.id, content)
    if (currentRequestVersion !== requestVersion) return
    const index = detail.value.comments.findIndex((item) => String(item.id) === String(updated.id))
    if (index >= 0) detail.value.comments.splice(index, 1, updated)
    cancelCommentEdit()
  } catch {
    if (currentRequestVersion === requestVersion) {
      commentMessage.value = '评论修改失败或无权操作'
    }
  } finally {
    if (currentRequestVersion === requestVersion) commentPendingId.value = null
  }
}

const handleDeleteComment = async (comment) => {
  if (!ownsComment(comment) || commentPendingId.value !== null) return

  const currentRequestVersion = requestVersion
  commentPendingId.value = String(comment.id)
  commentMessage.value = ''
  try {
    const deletedId = await http.deleteComment(comment.id)
    if (currentRequestVersion !== requestVersion) return
    detail.value.comments = detail.value.comments
      .filter((item) => String(item.id) !== String(deletedId))
    if (editingCommentId.value === String(deletedId)) cancelCommentEdit()
  } catch {
    if (currentRequestVersion === requestVersion) {
      commentMessage.value = '评论删除失败或无权操作'
    }
  } finally {
    if (currentRequestVersion === requestVersion) commentPendingId.value = null
  }
}

const loadDetail = async () => {
  const postId = props.postId
  if (!props.open || postId === null || postId === undefined) return
  const currentRequestVersion = ++requestVersion
  const isCurrentRequest = () => currentRequestVersion === requestVersion

  resetPostState()
  loading.value = true

  const viewerStateRequest = http.getViewerState(postId)
    .then((currentViewerState) => {
      if (isCurrentRequest()) viewerState.value = currentViewerState
      return true
    })
    .catch(() => {
      if (isCurrentRequest()) viewerStateUnavailable.value = true
      return false
    })

  try {
    const publicDetail = await http.getPublicDetail(postId)
    if (isCurrentRequest()) detail.value = publicDetail
  } catch {
    if (isCurrentRequest()) errorMessage.value = '帖子详情加载失败'
  } finally {
    if (isCurrentRequest()) loading.value = false
  }

  const authenticatedViewer = await viewerStateRequest
  if (authenticatedViewer && detail.value && isCurrentRequest() && http.recordView) {
    try {
      await http.recordView(postId)
    } catch {
      // 浏览记录是详情成功后的附属事实，失败不能阻断阅读。
    }
  }
}

watch(
  () => [props.open, props.postId],
  ([open, postId]) => {
    if (!open) {
      requestVersion += 1
      resetPostState()
      return
    }
    if (postId === null || postId === undefined) return
    loadDetail()
  },
  { immediate: true },
)
</script>

<style scoped>
.post-detail-backdrop {
  background: rgb(17 19 23 / 46%);
  inset: 0;
  padding: 3vh 3vw;
  position: fixed;
  z-index: 1000;
}

.post-detail {
  background: #fff;
  border-radius: var(--travel-radius-lg);
  box-shadow: var(--travel-shadow-float);
  height: 94vh;
  margin: 0 auto;
  max-width: 1320px;
  overflow: auto;
  position: relative;
}

.post-detail__close {
  align-items: center;
  background: #fff;
  border: 1px solid var(--travel-color-border);
  border-radius: 50%;
  color: var(--travel-color-text);
  cursor: pointer;
  display: flex;
  font-size: 16px;
  height: 38px;
  justify-content: center;
  position: sticky;
  float: right;
  right: 20px;
  top: 18px;
  width: 38px;
  z-index: 8;
}

.post-detail__state {
  align-items: center;
  color: var(--travel-color-text-secondary);
  display: flex;
  flex-direction: column;
  gap: 12px;
  justify-content: center;
  min-height: 60vh;
}

.post-detail__layout {
  display: grid;
  gap: 44px;
  grid-template-columns: minmax(0, 1fr) 300px;
  padding: 48px;
}

.post-detail__reader {
  min-width: 0;
}

.post-detail__back-label {
  color: var(--travel-color-brand);
  font-size: 13px;
  font-weight: 650;
}

.post-detail__heading h2 {
  color: var(--travel-color-text);
  font-size: clamp(30px, 4vw, 48px);
  letter-spacing: -.045em;
  line-height: 1.16;
  margin: 10px 0 18px;
}

.post-detail__byline,
.post-detail__author {
  align-items: center;
  display: flex;
}

.post-detail__byline {
  color: var(--travel-color-text-muted);
  font-size: 13px;
  gap: 16px;
}

.post-detail__author {
  background: transparent;
  border: 0;
  color: var(--travel-color-text);
  cursor: pointer;
  font: inherit;
  font-weight: 650;
  gap: 9px;
  padding: 0;
}

.post-detail__author img {
  border-radius: 50%;
  height: 36px;
  object-fit: cover;
  width: 36px;
}

.post-detail__images {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 30px 0 36px;
}

.post-detail__images img {
  aspect-ratio: 4 / 3;
  border-radius: var(--travel-radius-md);
  height: 100%;
  object-fit: cover;
  width: 100%;
}

.post-detail__images img:first-child {
  grid-column: 1 / -1;
  max-height: 520px;
}

.post-detail__copy h3,
.post-detail__section-heading h3 {
  font-size: 20px;
  margin: 0;
}

.post-detail__copy p {
  color: var(--travel-color-text-secondary);
  font-size: 16px;
  line-height: 1.9;
  margin: 14px 0 0;
  white-space: pre-wrap;
}

.post-detail__aside {
  align-self: start;
  display: grid;
  gap: 14px;
  position: sticky;
  top: 38px;
}

.post-detail__author-card,
.post-detail__interactions {
  border: 1px solid var(--travel-color-border);
  border-radius: var(--travel-radius-md);
  padding: 18px;
}

.post-detail__author-card {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.post-detail__author--large img {
  height: 44px;
  width: 44px;
}

.post-detail__follow {
  background: var(--travel-color-brand);
  border: 1px solid var(--travel-color-brand);
  border-radius: 9px;
  color: #fff;
  cursor: pointer;
  font-size: 13px;
  font-weight: 650;
  height: 36px;
  padding: 0 14px;
}

.post-detail__follow[aria-pressed="true"] {
  background: var(--travel-color-brand-soft);
  color: var(--travel-color-brand);
}

.post-detail__interactions {
  display: grid;
  gap: 2px;
}

.post-detail__interactions button {
  align-items: center;
  background: transparent;
  border: 0;
  border-radius: 8px;
  color: var(--travel-color-text);
  cursor: pointer;
  display: grid;
  font: inherit;
  grid-template-columns: 22px 1fr auto;
  padding: 11px 8px;
  text-align: left;
}

.post-detail__interactions button:hover,
.post-detail__interactions button[aria-pressed="true"] {
  background: var(--travel-color-brand-soft);
  color: var(--travel-color-brand);
}

.post-detail__status {
  color: var(--travel-color-brand-strong);
  font-size: 13px;
  margin: 0;
}

.post-detail__comments {
  border-top: 1px solid var(--travel-color-border);
  margin-top: 44px;
  padding-top: 30px;
}

.post-detail__section-heading {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.post-detail__section-heading span {
  color: var(--travel-color-text-muted);
  font-size: 13px;
}

.post-detail__comment-form {
  align-items: flex-end;
  background: var(--travel-color-bg-subtle);
  border: 1px solid var(--travel-color-border);
  border-radius: var(--travel-radius-md);
  display: flex;
  gap: 12px;
  margin-top: 20px;
  padding: 12px;
}

.post-detail__comment-form textarea,
.post-detail__comment-edit textarea {
  background: transparent;
  border: 0;
  flex: 1;
  min-height: 54px;
  outline: none;
  resize: vertical;
}

.post-detail__comment-list {
  list-style: none;
  margin: 22px 0 0;
  padding: 0;
}

.post-detail__comment-list li {
  border-top: 1px solid var(--travel-color-border);
  display: flex;
  gap: 12px;
  padding: 18px 0;
}

.post-detail__comment-list li > img {
  border-radius: 50%;
  height: 36px;
  object-fit: cover;
  width: 36px;
}

.post-detail__comment-content {
  display: grid;
  flex: 1;
  gap: 8px;
}

.post-detail__comment-meta {
  align-items: center;
  color: var(--travel-color-text-muted);
  display: flex;
  flex-wrap: wrap;
  font-size: 12px;
  gap: 8px;
}

.post-detail__comment-meta strong {
  color: var(--travel-color-text);
}

.post-detail__comment-actions button,
.post-detail__comment-edit button {
  background: transparent;
  border: 0;
  color: var(--travel-color-text-muted);
  cursor: pointer;
  font-size: 12px;
  padding: 0 12px 0 0;
}

@media (max-width: 899px) {
  .post-detail-backdrop {
    padding: 0;
  }

  .post-detail {
    border-radius: 0;
    height: 100vh;
  }

  .post-detail__layout {
    display: block;
    padding: 38px 20px 80px;
  }

  .post-detail__aside {
    margin-top: 28px;
    position: static;
  }
}
</style>

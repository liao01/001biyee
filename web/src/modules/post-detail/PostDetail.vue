<template>
  <section
    v-if="open"
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
      ×
    </button>

    <p v-if="loading">加载中…</p>

    <div v-else-if="errorMessage" role="alert">
      <p>{{ errorMessage }}</p>
      <button
        type="button"
        aria-label="重试帖子详情"
        @click="loadDetail"
      >
        重试
      </button>
    </div>

    <article v-else-if="detail">
      <h2 id="post-detail-title">{{ detail.post.title }}</h2>

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

      <div v-if="detail.images?.length" class="post-detail__images">
        <img
          v-for="(image, index) in detail.images"
          :key="image"
          :src="image"
          :alt="`${detail.post.title} 图片 ${index + 1}`"
        >
      </div>

      <p>{{ detail.post.description }}</p>

      <div class="post-detail__interactions">
        <button
          type="button"
          aria-label="点赞"
          :aria-pressed="viewerState?.liked ?? false"
          :disabled="likePending"
          @click="handleLike"
        >
          点赞 {{ detail.interactionCounts.like }}
        </button>
        <button
          type="button"
          aria-label="收藏"
          :aria-pressed="viewerState?.favorited ?? false"
          :disabled="favoritePending"
          @click="handleFavorite"
        >
          收藏 {{ detail.interactionCounts.favorite }}
        </button>
        <button
          v-if="!viewerState?.selfAuthor"
          type="button"
          aria-label="关注作者"
          :aria-pressed="viewerState?.followed ?? false"
          :disabled="followPending"
          @click="handleFollow"
        >
          {{ viewerState?.followed ? '已关注' : '关注' }}
        </button>
      </div>
      <p v-if="viewerStateUnavailable || interactionMessage" role="status">
        {{ interactionMessage || '登录后可查看个人互动状态' }}
      </p>

      <section aria-labelledby="post-detail-comments-title">
        <h3 id="post-detail-comments-title">评论</h3>
        <div v-if="viewerState" class="post-detail__comment-form">
          <textarea
            v-model="commentDraft"
            aria-label="添加评论内容"
          />
          <button
            type="button"
            aria-label="发布评论"
            :disabled="commentPending || !commentDraft.trim()"
            @click="handleCreateComment"
          >
            发布
          </button>
        </div>
        <p
          v-if="commentMessage"
          role="alert"
          aria-label="评论操作反馈"
        >
          {{ commentMessage }}
        </p>
        <ul>
          <li
            v-for="comment in detail.comments"
            :key="comment.id"
            :data-comment-id="comment.id"
          >
            <img
              v-if="comment.avatar"
              :src="comment.avatar"
              :alt="`${comment.membername}的头像`"
            >
            <small>#{{ comment.id }}</small>
            <strong>{{ comment.membername }}</strong>
            <time v-if="comment.commentTime" :datetime="comment.commentTime">
              {{ comment.commentTime }}
            </time>
            <span>{{ comment.commentContent }}</span>
            <div v-if="editingCommentId === String(comment.id)">
              <textarea
                v-model="editCommentDraft"
                :aria-label="`编辑评论内容 ${comment.id}`"
              />
              <button
                type="button"
                :aria-label="`保存评论 ${comment.id}`"
                :disabled="commentPendingId !== null || !editCommentDraft.trim()"
                @click="handleUpdateComment(comment)"
              >
                保存
              </button>
              <button
                type="button"
                :aria-label="`取消编辑评论 ${comment.id}`"
                :disabled="commentPendingId !== null"
                @click="cancelCommentEdit"
              >
                取消
              </button>
            </div>
            <template v-if="ownsComment(comment)">
              <button
                v-if="editingCommentId !== String(comment.id)"
                type="button"
                :aria-label="`编辑评论 ${comment.id}`"
                :disabled="commentPendingId !== null"
                @click="startCommentEdit(comment)"
              >
                编辑
              </button>
              <button
                type="button"
                :aria-label="`删除评论 ${comment.id}`"
                :disabled="commentPendingId !== null"
                @click="handleDeleteComment(comment)"
              >
                删除
              </button>
            </template>
          </li>
        </ul>
      </section>
    </article>
  </section>
</template>

<script setup>
import { inject, ref, watch } from 'vue'

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

const runViewerMutation = async ({ pending, currentActive, write, apply, failureMessage }) => {
  if (!viewerState.value) {
    interactionMessage.value = '请先登录后再互动'
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
.post-detail {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgb(0 0 0 / 20%);
  inset: 5vh 5vw;
  overflow: auto;
  padding: 24px;
  position: fixed;
  z-index: 1000;
}

.post-detail__author {
  align-items: center;
  background: transparent;
  border: 0;
  cursor: pointer;
  display: flex;
  gap: 12px;
}

.post-detail__close {
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: 30px;
  position: absolute;
  right: 20px;
  top: 12px;
}

.post-detail__author img {
  border-radius: 50%;
  height: 48px;
  object-fit: cover;
  width: 48px;
}

.post-detail__images {
  display: flex;
  gap: 12px;
  margin-block: 20px;
  overflow-x: auto;
}

.post-detail__images img {
  border-radius: 8px;
  max-height: 420px;
  object-fit: cover;
  width: min(100%, 640px);
}

.post-detail__interactions {
  display: flex;
  gap: 12px;
  margin-block: 20px;
}

.post-detail li img {
  border-radius: 50%;
  height: 32px;
  object-fit: cover;
  width: 32px;
}
</style>

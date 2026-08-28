<template>
  <div class="travel-page content-list-page">
    <header class="travel-page__header">
      <div>
        <h1 class="travel-page__title">{{ title }}</h1>
        <p v-if="subtitle" class="travel-page__subtitle">{{ subtitle }}</p>
      </div>
    </header>

    <div v-if="loading" class="travel-empty">{{ loadingText }}</div>
    <div v-else-if="!hasContent" class="travel-empty">{{ emptyText }}</div>
    <section v-else-if="usesPostPreviews" class="content-list-page__grid" :aria-label="listLabel">
      <PostPreview
        v-for="post in posts"
        :key="post.id"
        :post="post"
        @open="openPostDetail"
      />
    </section>
    <slot v-else />

    <PostDetail v-if="usesPostPreviews" v-model:open="detailOpen" :post-id="selectedPostId" />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

import PostDetail from '../../modules/post-detail/PostDetail.vue'
import PostPreview from './PostPreview.vue'

const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  loadingText: { type: String, default: '正在加载旅行内容…' },
  emptyText: { type: String, required: true },
  listLabel: { type: String, default: '帖子列表' },
  posts: { type: Array, default: null },
  hasCustomContent: { type: Boolean, default: false },
})

const usesPostPreviews = computed(() => Array.isArray(props.posts))
const hasContent = computed(() => (
  usesPostPreviews.value ? props.posts.length > 0 : props.hasCustomContent
))

const selectedPostId = ref(null)
const detailOpen = ref(false)

const openPostDetail = (post) => {
  selectedPostId.value = post.id
  detailOpen.value = true
}
</script>

<style scoped>
.content-list-page__grid {
  column-count: 4;
  column-gap: 22px;
}

@media (max-width: 1399px) {
  .content-list-page__grid { column-count: 3; }
}

@media (max-width: 999px) {
  .content-list-page__grid { column-count: 2; }
}

@media (max-width: 560px) {
  .content-list-page__grid { column-count: 1; }
}
</style>

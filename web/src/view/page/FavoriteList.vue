<template>
  <div class="travel-page collection-page">
    <header class="travel-page__header">
      <div>
        <h1 class="travel-page__title">我的收藏</h1>
        <p class="travel-page__subtitle">把喜欢的风景和路线，留给下一次出发。</p>
      </div>
    </header>

    <div v-if="loading" class="travel-empty">正在加载收藏…</div>
    <div v-else-if="!cardList.length" class="travel-empty">还没有收藏旅行内容。</div>
    <section v-else class="collection-grid" aria-label="收藏帖子列表">
      <PostCard v-for="item in cardList" :key="item.id" :post="item" @open="showCardModal" />
    </section>

    <PostDetail v-model:open="detailOpen" :post-id="selectedPostId" />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import PostDetail from "../../modules/post-detail/PostDetail.vue"
import PostCard from '../../components/travel/PostCard.vue'
import axios from "axios"
import {notification} from "ant-design-vue"
import { useSearchStore } from "../../store/search.js"
import { BASE_URL } from "../../utils/baseUrl";

const selectedPostId = ref(null)
const detailOpen = ref(false)
const baseUrl = BASE_URL+'/lyw'
const cardList = ref([])
const loading = ref(false)
const MAX_LENGTH = 70

const searchStore = useSearchStore()

// 通用加载函数
const loadPosts = async (keyword = '') => {
  loading.value = true
  try {
    const url = keyword ? `${baseUrl}/web/post/post-search` : `${baseUrl}/web/userAction/favorite`
    const { data } = await axios.get(url, { params: keyword ? { keyword } : {} })
    if (data.success) {
      cardList.value = (data.content || []).map(post => ({
        id: post.postId,
        raw: post,
        image: baseUrl + (post.imageUrls?.split(',')[0] || ''),
        title: post.postTitle,
        description: (post.postContent || '').length > MAX_LENGTH
            ? post.postContent.substring(0, MAX_LENGTH) + '...'
            : (post.postContent || ''),
        author: post.postMembername,
        location: post.locationName || post.postLocation || '旅行记录',
        publishedAt: post.postTime,
      }))
    } else {
      notification.error({ description: data.message })
    }
  } catch (e) {
    notification.error({ description: '请求失败' })
  } finally {
    loading.value = false
  }
}

// 页面首次加载全部帖子
onMounted(() => {
  loadPosts()
})

// 监听全局搜索关键字变化
watch(() => searchStore.keyword, (newKeyword) => {
  loadPosts(newKeyword)
})

// 点击卡片显示子组件模态框
const showCardModal = (item) => {
  selectedPostId.value = item.id || item.raw?.postId
  detailOpen.value = true
}
</script>

<style scoped>
.collection-grid {
  column-count: 4;
  column-gap: 22px;
}

@media (max-width: 1399px) {
  .collection-grid { column-count: 3; }
}

@media (max-width: 999px) {
  .collection-grid { column-count: 2; }
}

@media (max-width: 560px) {
  .collection-grid { column-count: 1; }
}
</style>

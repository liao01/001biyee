<template>
  <div class="travel-page discovery-page">
    <div class="travel-tabs discovery-page__tabs" aria-label="内容分类">
      <button
        v-for="category in categories"
        :key="category"
        :class="['travel-tab', { 'is-active': activeCategory === category }]"
        type="button"
        @click="activeCategory = category"
      >
        {{ category }}
      </button>
    </div>

    <div v-if="loading" class="travel-empty">正在整理旅行灵感…</div>
    <div v-else-if="!cardList.length" class="travel-empty">
      <CompassOutlined />
      <p>暂时没有找到相关旅行记录，换个关键词试试。</p>
    </div>
    <section v-else class="discovery-grid" aria-label="旅行帖子列表">
      <PostCard
        v-for="item in cardList"
        :key="item.id"
        :post="item"
        @open="showCardModal"
      />
    </section>

    <PostDetail v-model:open="detailOpen" :post-id="selectedPostId" />
  </div>
</template>

<script setup>
/* ... 保持原有逻辑不变 ... */
import { ref, onMounted, watch } from 'vue'
import { CompassOutlined } from '@ant-design/icons-vue'
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
const activeCategory = ref('推荐')
const categories = ['推荐', '最新', '城市漫游', '自然风光', '美食']
const MAX_LENGTH = 70

const searchStore = useSearchStore()

const loadPosts = async (keyword = '') => {
  loading.value = true
  try {
    const url = keyword ? `${baseUrl}/web/post/post-search` : `${baseUrl}/web/post/post-findAll`
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

onMounted(() => { loadPosts() })
watch(() => searchStore.keyword, (newKeyword) => { loadPosts(newKeyword) })

const showCardModal = (item) => {
  selectedPostId.value = item.id || item.raw?.postId
  detailOpen.value = true
}
</script>

<style scoped>
.discovery-page__tabs {
  margin-bottom: 26px;
}

.discovery-grid {
  column-count: 4;
  column-gap: 22px;
}

.travel-empty :deep(.anticon) {
  color: var(--travel-color-brand);
  font-size: 28px;
}

@media (max-width: 1399px) {
  .discovery-grid {
    column-count: 3;
  }
}

@media (max-width: 999px) {
  .discovery-grid {
    column-count: 2;
  }
}

@media (max-width: 560px) {
  .discovery-grid {
    column-count: 1;
  }
}
</style>

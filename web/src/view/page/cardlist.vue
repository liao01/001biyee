<template>
  <div class="travel-page discovery-page">
    <div class="travel-tabs discovery-page__tabs" aria-label="内容分类">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        :class="['travel-tab', { 'is-active': activeTabKey === tab.key }]"
        type="button"
        @click="selectTab(tab)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div v-if="loading" class="travel-empty">正在整理旅行灵感…</div>
    <div v-else-if="!postPreviews.length" class="travel-empty">
      <CompassOutlined />
      <p>暂时没有找到相关旅行记录，换个关键词试试。</p>
    </div>
    <section v-else class="discovery-grid" aria-label="旅行帖子列表">
      <PostPreview
        v-for="item in postPreviews"
        :key="item.id"
        :post="item"
        @open="showPostDetail"
      />
    </section>

    <PostDetail v-model:open="detailOpen" :post-id="selectedPostId" />
  </div>
</template>

<script setup>
/* ... 保持原有逻辑不变 ... */
import { computed, ref, onMounted, watch } from 'vue'
import { CompassOutlined } from '@ant-design/icons-vue'
import PostDetail from "../../modules/post-detail/PostDetail.vue"
import PostPreview from '../../components/travel/PostPreview.vue'
import { toPostPreview } from '../../components/travel/postPreviewAdapter.js'
import { buildDiscoveryParams, fetchPostCategories } from '../../api/postCategories.js'
import axios from "axios"
import {notification} from "ant-design-vue"
import { useSearchStore } from "../../store/search.js"
import { BASE_URL } from "../../utils/baseUrl";

const selectedPostId = ref(null)
const detailOpen = ref(false)
const baseUrl = BASE_URL+'/lyw'
const postPreviews = ref([])
const loading = ref(false)
const activeTabKey = ref('RECOMMENDED')
const categoryOptions = ref([])
const viewTabs = [
  { key: 'RECOMMENDED', label: '推荐', view: 'RECOMMENDED' },
  { key: 'LATEST', label: '最新', view: 'LATEST' },
]
const tabs = computed(() => [
  ...viewTabs,
  ...categoryOptions.value.map(category => ({
    key: category.code,
    label: category.name,
    categoryCode: category.code,
  })),
])

const searchStore = useSearchStore()

const activeTab = () => tabs.value.find(tab => tab.key === activeTabKey.value) || viewTabs[0]

const loadPosts = async (keyword = '') => {
  loading.value = true
  try {
    const url = keyword ? `${baseUrl}/web/post/post-search` : `${baseUrl}/web/post/post-findAll`
    const params = keyword ? { keyword } : buildDiscoveryParams(activeTab())
    const { data } = await axios.get(url, { params })
    if (data.success) {
      postPreviews.value = (data.content || []).map(post => toPostPreview(post, { baseUrl }))
    } else {
      notification.error({ description: data.message })
    }
  } catch (e) {
    notification.error({ description: '请求失败' })
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  try {
    categoryOptions.value = await fetchPostCategories(axios, baseUrl)
  } catch (error) {
    notification.error({ description: error.message || '分类加载失败' })
  }
}

const selectTab = (tab) => {
  activeTabKey.value = tab.key
  loadPosts(searchStore.keyword)
}

onMounted(async () => {
  await loadCategories()
  await loadPosts()
})
watch(() => searchStore.keyword, (newKeyword) => { loadPosts(newKeyword) })

const showPostDetail = (item) => {
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

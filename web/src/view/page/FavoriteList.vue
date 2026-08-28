<template>
  <ContentListPage
    title="我的收藏"
    subtitle="把喜欢的风景和路线，留给下一次出发。"
    :loading="loading"
    loading-text="正在加载收藏…"
    empty-text="还没有收藏旅行内容。"
    list-label="收藏帖子列表"
    :posts="postPreviews"
  />
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import ContentListPage from '../../components/travel/ContentListPage.vue'
import { toPostPreview } from '../../components/travel/postPreviewAdapter.js'
import axios from "axios"
import {notification} from "ant-design-vue"
import { useSearchStore } from "../../store/search.js"
import { BASE_URL } from "../../utils/baseUrl";

const baseUrl = BASE_URL+'/lyw'
const postPreviews = ref([])
const loading = ref(false)

const searchStore = useSearchStore()

// 通用加载函数
const loadPosts = async (keyword = '') => {
  loading.value = true
  try {
    const url = keyword ? `${baseUrl}/web/post/post-search` : `${baseUrl}/web/userAction/favorite`
    const { data } = await axios.get(url, { params: keyword ? { keyword } : {} })
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

// 页面首次加载全部帖子
onMounted(() => {
  loadPosts()
})

// 监听全局搜索关键字变化
watch(() => searchStore.keyword, (newKeyword) => {
  loadPosts(newKeyword)
})

</script>

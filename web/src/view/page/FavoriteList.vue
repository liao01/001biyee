<template>
  <div style="padding: 20px;">
    <Waterfall
        :list="cardList"
        :width="240"
        :gutter="16"
    >
      <template #item="{ item }">
        <a-card
            hoverable
            style="width: 240px"
            @click="showCardModal(item)"
        >
          <template #cover>
            <img :src="baseUrl + item.raw.imageUrls?.split(',')[0]" :alt="item.title" />
          </template>
          <a-card-meta :title="item.title">
            <template #description>{{ item.description }}</template>
          </a-card-meta>
        </a-card>
      </template>
    </Waterfall>

    <!-- 子组件模态框 -->
    <FavoriteDetail ref="cardFileRef" />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { Waterfall } from 'vue-waterfall-plugin-next'
import 'vue-waterfall-plugin-next/dist/style.css'
import FavoriteDetail from "../../components/card/FavoriteDetail.vue"
import axios from "axios"
import {message, notification} from "ant-design-vue"
import { useSearchStore } from "../../store/search.js"

const cardFileRef = ref(null)
const baseUrl = 'http://localhost:8080/lyw'
const cardList = ref([])
const MAX_LENGTH = 10

const searchStore = useSearchStore()

// 通用加载函数
const loadPosts = async (keyword = '') => {
  try {
    const url = keyword ? `${baseUrl}/web/post/post-search` : `${baseUrl}/web/userAction/favorite`
    const { data } = await axios.get(url, { params: keyword ? { keyword } : {} })
    if (data.success) {
      cardList.value = (data.content || []).map(post => ({
        raw: post,
        title: post.postTitle,
        description: post.postContent.length > MAX_LENGTH
            ? post.postContent.substring(0, MAX_LENGTH) + '...'
            : post.postContent,
        membername: post.postMembername,
        postTime: post.postTime
      }))
    } else {
      notification.error({ description: data.message })
    }
  } catch (e) {
    notification.error({ description: '请求失败' })
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
  const fullItem = {
    title: item.raw.postTitle,
    description: item.raw.postContent,
    images: item.raw.imageUrls?.split(',') || [],
    membername: item.raw.membername,
    postTime: item.raw.postTime,
    postId: item.raw.postId,
    userId: item.raw.userId,
    avatar: item.raw.avatar
  }

  axios.post("http://localhost:8080/lyw/web/postview/save", {
    postId : item.raw.postId
  }).then(response => {
    const data = response.data;
    if (data.success) {
      message.success("记录成功!");
      console.log("登录返回数据", data.content);
    } else {
      message.error(data.message)
    }
  })

  cardFileRef.value.showModal(fullItem)

}
</script>

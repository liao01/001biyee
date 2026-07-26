<template>
  <div class="waterfall-container">
    <Waterfall
        :list="cardList"
        :width="240"
        :gutter="16"
        class="waterfall-box"
    >
      <template #item="{ item }">
        <a-card
            hoverable
            class="custom-card"
            @click="showCardModal(item)"
        >
          <template #cover>
            <div class="cover-wrapper">
              <img :src="baseUrl + item.raw.imageUrls?.split(',')[0]" :alt="item.title" />
            </div>
          </template>
          <a-card-meta :title="item.title">
            <template #description>
              <span class="card-desc">{{ item.description }}</span>
            </template>
          </a-card-meta>
        </a-card>
      </template>
    </Waterfall>

    <!-- 子组件模态框 -->
    <CardFileView ref="cardFileRef" />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { Waterfall } from 'vue-waterfall-plugin-next'
import 'vue-waterfall-plugin-next/dist/style.css'
import CardFileView from "../../components/card/card-fileView.vue"
import axios from "axios"
import {message, notification} from "ant-design-vue"
import { useSearchStore } from "../../store/search.js"
import store from "../../store/index.js";
import { BASE_URL } from "../../utils/baseUrl";

const cardFileRef = ref(null)
const baseUrl = BASE_URL+'/lyw'
const cardList = ref([])
const MAX_LENGTH = 10

const searchStore = useSearchStore()

// 通用加载函数
const loadPosts = async (keyword = '') => {
  try {
    const url = keyword ? `${baseUrl}/web/post/post-search` : `${baseUrl}/web/postview/find`
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

  axios.post(BASE_URL+"/lyw/web/postview/save", {
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

<style scoped>
/* 1. 容器背景与内边距 */
.waterfall-container {
  padding: 30px;
  background-color: #f4f7f9; /* 浅冷色调背景，让白色卡片更突出 */
  min-height: 100vh;
}

/* 2. 卡片整体样式重塑 */
:deep(.custom-card) {
  border-radius: 12px; /* 更圆润的角 */
  overflow: hidden;
  border: none; /* 去掉生硬的边框 */
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); /* 柔和的阴影 */
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1) !important;
  background: #ffffff;
}

/* 3. 悬停动效：上浮并加深阴影 */
:deep(.custom-card:hover) {
  transform: translateY(-8px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.12);
}

/* 4. 图片封面处理 */
.cover-wrapper {
  overflow: hidden;
  line-height: 0;
}

.cover-wrapper img {
  width: 100%;
  height: auto;
  transition: transform 0.5s ease;
  object-fit: cover;
}

/* 悬停时图片轻微缩放 */
:deep(.custom-card:hover) .cover-wrapper img {
  transform: scale(1.08);
}

/* 5. 内容区域调整 */
:deep(.ant-card-meta-title) {
  font-size: 16px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 8px !important;
}

:deep(.ant-card-meta-description) {
  color: #8c8c8c;
  font-size: 13px;
  line-height: 1.5;
}

/* 6. 强制瀑布流居中显示 */
.waterfall-box {
  margin: 0 auto;
}

/* 7. 卡片内边距微调 */
:deep(.ant-card-body) {
  padding: 16px !important;
}
</style>
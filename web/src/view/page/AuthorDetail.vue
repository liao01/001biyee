<template>
  <div class="user-page">
    <div class="main-container">

      <div class="user-header-wrapper">
        <a-avatar
            :src="user.avatar ? baseUrl + user.avatar : defaultAvatar"
            :size="100"
            class="avatar"
        />
        <div class="user-info">
          <h2>{{ user.username }}</h2>
          <p> IP属地：{{ user.location || '未填写' }}</p>
          <div class="user-stats">
            <div class="stat-item">
              <span class="stat-num">{{ following || 0 }}</span>
              <span class="stat-label">关注</span>
            </div>
            <div class="stat-item">
              <span class="stat-num">{{ statistic.countFollowers || 0 }}</span>
              <span class="stat-label">粉丝</span>
            </div>
            <div class="stat-item">
              <span class="stat-num">{{ likecount || 0 }}</span>
              <span class="stat-label">获赞</span>
            </div>
          </div>
        </div>
      </div>

      <div class="user-tabs">
        <button
            class="tab"
            :class="{ active: activeTab === 'note' }"
            @click="switchTab('note')">
          笔记
        </button>

        <button
            class="tab"
            :class="{ active: activeTab === 'favorite' }"
            @click="switchTab('favorite')">
          收藏
        </button>
      </div>

      <div class="waterfall-wrapper">
        <Waterfall
            :list="cardList"
            :width="280"
            :gutter="16"
            :has-around-gutter="true"
            background-color="transparent"
        >
          <template #item="{ item }">
            <a-card hoverable class="note-card" @click="showCardModal(item)">
              <template #cover>
                <img :src="baseUrl + item.raw.imageUrls?.split(',')[0]" :alt="item.title" />
              </template>
              <a-card-meta :title="item.title">
                <template #description>{{ item.description }}</template>
              </a-card-meta>
            </a-card>
          </template>
        </Waterfall>
      </div>
    </div>

    <PostDetail v-model:open="detailOpen" :post-id="selectedPostId" />
  </div>
</template>


<script setup>
import { computed, ref, watch } from 'vue'
import {message, notification} from 'ant-design-vue'
import axios from 'axios'
import { useRoute } from 'vue-router'
import {Waterfall} from "vue-waterfall-plugin-next";
import PostDetail from "../../modules/post-detail/PostDetail.vue";
import { BASE_URL } from "../../utils/baseUrl";

const baseUrl = BASE_URL+'/lyw'
const defaultAvatar = ''
const route = useRoute()
// 用户数据
const user = ref({})
const userId = computed(() => String(route.params.authorId || ''))
const cardList = ref([])
const selectedPostId = ref(null)
const detailOpen = ref(false)
const likecount = ref(0)
const following = ref({})
const activeTab = ref('note')

const MAX_LENGTH = 10
const statistic = ref({});

const fetchStatistic = async () => {
  try {
    const response = await axios.get(BASE_URL+"/lyw/web/userfollow/query-statistic");
    if (response.data.success) {
      statistic.value = response.data.content;
    } else {
      notification.error({ description: response.data.message });
    }
  } catch (err) {
    notification.error({ description: "数据请求失败" });
  }
};

const switchTab = (tab) => {
  if (activeTab.value === tab) return; // 已选中，不重复请求
  activeTab.value = tab;

  if (tab === 'note') {
    fetchUserPosts();
  } else if (tab === 'favorite') {
    fetchUserFavorites();
  }
};

const fetchUserPosts = async () => {
  try {
    const response = await axios.post(BASE_URL+"/lyw/web/post/post-UserPostQuery", {
      userid: userId.value
    });
    const data = response.data;
    if (data.success) {
      cardList.value = (data.content || []).map(post => ({
        raw: post,
        title: post.postTitle,
        description: post.postContent.length > MAX_LENGTH ? post.postContent.substring(0, MAX_LENGTH) + '...' : post.postContent,
        membername: post.postMembername,
        postTime: post.postTime
      }));
    } else {
      message.error(data.message);
    }
  } catch (err) {
    console.error(err);
    message.error('请求失败');
  }
};

const fetchUserFavorites = async () => {
  try {
    const response = await axios.post(BASE_URL+"/lyw/web/post/post-list-Favorite-Posts", {
      userid: userId.value
    });
    const data = response.data;
    if (data.success) {
      cardList.value = (data.content || []).map(post => ({
        raw: post,
        title: post.postTitle,
        description: post.postContent.length > MAX_LENGTH ? post.postContent.substring(0, MAX_LENGTH) + '...' : post.postContent,
        membername: post.postMembername,
        postTime: post.postTime
      }));
    } else {
      message.error(data.message);
    }
  } catch (err) {
    console.error(err);
    message.error('请求失败');
  }
};

const loadAuthorPage = () => {
  if (!userId.value) return
  axios.get(BASE_URL+"/lyw/web/UserProFile/findAllUser", {
    params: {
      userId: userId.value
    }
  }).then(response => {
    const data = response.data
    if (data.success) {
      user.value =  data.content[0]
    } else {
      message.error(data.message)
    }
  }).catch(err => {
    console.error(err)
    message.error('请求失败')
  })

  axios.get(BASE_URL+"/lyw/web/userAction/User-Like-Count", {
    params: { userId: userId.value }
  }).then(response => {
    const data = response.data;
    if (data.success) {
      // content 是数字，直接赋值
      likecount.value = data.content;
      console.log("用户获赞数:", likecount.value);
    } else {
      message.error(data.message);
    }
  }).catch(err => {
    console.error(err);
    message.error('请求失败');
  })

  axios.get(BASE_URL+"/lyw/web/userfollow/byUserIds", {
    params: { userId: userId.value }
  }).then(response => {
    const data = response.data;
    if (data.success) {
      // content 是数字，直接赋值
      following.value = data.content;
      console.log("用户关注数:", following.value);
    } else {
      message.error(data.message);
    }
  }).catch(err => {
    console.error(err);
    message.error('请求失败');
  })

  fetchUserPosts();
  fetchStatistic();
}

watch(() => route.params.authorId, loadAuthorPage, { immediate: true })

// 格式化性别
const genderText = (val) => {
  switch (val) {
    case 1:
      return '男'
    case 2:
      return '女'
    default:
      return '未知'
  }
}

// 格式化生日
const formatDate = (isoDate) => {
  if (!isoDate) return '未填写'
  const date = new Date(isoDate)
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}

const showCardModal = (item) => {
  selectedPostId.value = item.raw.postId
  detailOpen.value = true
}
</script>

<style scoped>
/* 全局背景和对齐 */
.user-page {
  background-color: #f7f8fa; /* 稍微深一点的底色，衬托白色的卡片 */
  min-height: 100vh;
  width: 100%;
}

/* 核心容器：限制最大宽度并居中，解决“突出来”的关键 */
.main-container {
  max-width: 1200px;
  margin: 0 auto;
  background-color: #ffffff;
  min-height: 100vh;
  box-shadow: 0 0 20px rgba(0,0,0,0.02); /* 侧边淡淡的阴影，增加高级感 */
}

/* 用户信息区域美化 */
.user-header-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  padding: 60px 20px 30px;
  background: linear-gradient(to bottom, #f8f9ff 0%, #ffffff 100%);
  gap: 16px;
}

.avatar {
  border: 4px solid #fff;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);
}

.user-info {
  text-align: center;
}

.user-info h2 {
  font-size: 26px;
  font-weight: 700;
  margin-bottom: 8px;
}

/* 统计项美化 */
.user-stats {
  margin-top: 20px;
  display: flex;
  gap: 40px;
}
.stat-item {
  display: flex;
  flex-direction: column;
  cursor: pointer;
}
.stat-num {
  font-size: 18px;
  font-weight: 700;
  color: #1a1a1a;
}
.stat-label {
  font-size: 13px;
  color: #999;
}

/* Tab 切换栏 */
.user-tabs {
  display: flex;
  justify-content: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 20px;
  position: sticky;
  top: 0;
  background: #fff;
  z-index: 10;
}

.tab {
  padding: 8px 30px;
  border-radius: 20px;
  font-size: 15px;
  border: none;
  background: none;
  cursor: pointer;
  color: #666;
  transition: all 0.3s;
}

.tab.active {
  background: #000;
  color: #fff;
  font-weight: 600;
}

/* 瀑布流容器：增加内边距，不让卡片贴边 */
.waterfall-wrapper {
  padding: 0 24px 40px 24px;
}

/* 卡片样式优化 */
.note-card {
  border: none !important;
  border-radius: 12px !important;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04) !important;
  transition: transform 0.3s ease !important;
}

.note-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.08) !important;
}

:deep(.ant-card-cover img) {
  border-radius: 12px 12px 0 0;
}

</style>

<template>
  <div class="user-page">

    <!-- 顶部用户信息 -->
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
          <span>{{ following }} 关注</span>
          <span>{{ statistic.countFollowers  }} 粉丝</span>
          <span>{{ likecount }} 获赞</span>
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

    <!-- 瀑布流 -->
    <div class="waterfall-wrapper">
      <Waterfall :list="cardList" :width="240" :gutter="16">
        <template #item="{ item }">
          <a-card hoverable style="width: 240px" @click="showCardModal(item)">
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



    <CardFile ref="cardFileRef" />
  </div>
</template>


<script setup>
import { ref, onMounted } from 'vue'
import {message, notification} from 'ant-design-vue'
import axios from 'axios'
import store from "../../store/index.js";
import {Waterfall} from "vue-waterfall-plugin-next";
import CardFile from "../../components/card/card-file.vue";

const baseUrl = 'http://localhost:8080/lyw'
// 用户数据
const user = ref({})
const userId = ref(null)
const cardList = ref([])
const cardFileRef = ref(null)
const likecount = ref(0)
const following = ref({})
const activeTab = ref('note')

const MAX_LENGTH = 10
const statistic = ref({});

const fetchStatistic = async () => {
  try {
    const response = await axios.get("http://localhost:8080/lyw/web/userfollow/query-statistic");
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
    const response = await axios.post("http://localhost:8080/lyw/web/post/post-UserPostQuery", {
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
    const response = await axios.post("http://localhost:8080/lyw/web/post/post-list-Favorite-Posts", {
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

onMounted(() => {
  userId.value = sessionStorage.getItem('authorId');

  axios.get("http://localhost:8080/lyw/web/UserProFile/findAllUser", {
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

  axios.get("http://localhost:8080/lyw/web/userAction/User-Like-Count", {
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

  axios.get("http://localhost:8080/lyw/web/userfollow/byUserIds", {
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

  axios.post("http://localhost:8080/lyw/web/post/post-UserPostQuery", {
    userid: userId.value
  }).then(response => {
    const data = response.data;
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
      message.error(data.message)
    }
  })

  fetchUserPosts();

  fetchStatistic();
})

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
  const fullItem = {
    title: item.raw.postTitle,
    description: item.raw.postContent,
    images: item.raw.imageUrls?.split(',') || [],
    membername: item.raw.membername,
    postTime: item.raw.postTime,
    postId: item.raw.postId,
    userId: item.raw.userId,
    avatar: item.raw.avatar,
  }

  axios.post("http://localhost:8080/lyw/web/postview/save", {
    postId : item.raw.postId
  }).then(response => {
    const data = response.data;
    if (data.success) {
      message.success("记录成功!");
      console.log("登录返回数据", data.content);
    }
  })

  cardFileRef.value.showModal(fullItem)

}
</script>

<style scoped>
/* 全局背景微调 */
.user-page {
  background-color: #fbfbfc;
  min-height: 100vh;
}

/* 顶部用户信息区域美化 */
.user-header-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  padding: 60px 20px 40px;
  background: white;
  /* 增加一个非常淡的背景渐变，增加深度感 */
  background: linear-gradient(to bottom, #f8f9ff 0%, #ffffff 100%);
  gap: 16px;
  position: relative;
}

/* 头像外圈装饰 */
.avatar {
  border: 4px solid #fff;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
  transition: transform 0.3s ease;
  cursor: pointer;
}

.avatar:hover {
  transform: scale(1.05);
}

.user-info h2 {
  margin: 10px 0 5px;
  font-size: 28px;
  font-weight: 800;
  color: #1a1a1a;
  letter-spacing: -0.5px;
}

.user-info p {
  margin: 0;
  color: #999;
  font-size: 13px;
  background: #f0f2f5;
  padding: 2px 12px;
  border-radius: 12px;
  display: inline-block;
}

/* 统计数据栏 */
.user-stats {
  margin-top: 20px;
  display: flex;
  gap: 32px; /* 加大间距 */
}

.user-stats span {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  transition: color 0.2s;
}

/* 让数字和文字有主次之分 */
.user-stats span::before {
  content: attr(data-count); /* 如果以后你想用JS传值可以直接这样写，现在先手动调样式 */
  font-size: 18px;
  font-weight: 700;
  color: #1a1a1a;
  margin-bottom: 2px;
}

/* Tab 切换栏美化 */
.user-tabs {
  display: flex;
  justify-content: center;
  margin: 0;
  padding: 20px 0;
  background: #fff;
  position: sticky; /* 粘性定位：滚动时固定在顶部 */
  top: 0;
  z-index: 100;
  border-bottom: 1px solid rgba(0,0,0,0.04);
}

.tab {
  padding: 8px 32px;
  border-radius: 25px;
  font-size: 15px;
  font-weight: 500;
  border: none;
  cursor: pointer;
  background: transparent;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  color: #999;
  position: relative;
}

.tab.active {
  background: #1a1a1a; /* 黑色深沉风格 */
  color: #fff;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.tab:not(.active):hover {
  background: #f5f5f7;
  color: #333;
}

/* 瀑布流卡片精致化 */
.waterfall-wrapper {
  max-width: 1200px;
  margin: 0 auto;
  padding: 30px 20px;
}

/* 穿透修改 antd 卡片样式 */
:deep(.ant-card) {
  border: none !important;
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
  transition: all 0.3s ease !important;
}

:deep(.ant-card:hover) {
  transform: translateY(-8px);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.08) !important;
}

:deep(.ant-card-cover img) {
  border-radius: 16px 16px 0 0;
  transition: transform 0.5s ease;
}

:deep(.ant-card:hover .ant-card-cover img) {
  transform: scale(1.05); /* 悬停图片轻微放大 */
}

:deep(.ant-card-meta-title) {
  font-size: 15px !important;
  font-weight: 600 !important;
  margin-bottom: 8px !important;
}

:deep(.ant-card-meta-description) {
  font-size: 13px !important;
  color: #666 !important;
  line-height: 1.5;
}

/* 响应式微调 */
@media (max-width: 768px) {
  .user-stats {
    gap: 20px;
  }
  .user-info h2 {
    font-size: 22px;
  }
}
</style>


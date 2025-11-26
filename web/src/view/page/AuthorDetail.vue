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
          <span>{{ user.following }} 关注</span>
          <span>{{ statistic.countFollowers  }} 粉丝</span>
          <span>{{ likecount }} 获赞</span>
        </div>
      </div>
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
    } else {
      message.error(data.message)
    }
  })

  cardFileRef.value.showModal(fullItem)

}
</script>

<style scoped>
.user-header-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;   /* 水平居中 */
  flex-direction: column;    /* 改为上下排列 */
  padding: 20px;
  gap: 20px;
  border-bottom: 1px solid #f0f0f0;
  text-align: center;        /* 文本居中 */
}

.user-info h2 {
  margin: 0;
  font-size: 24px;
  font-weight: bold;
}

.user-info p {
  margin: 4px 0;
  color: #888;
}

.user-stats {
  margin-top: 8px;
  display: flex;
  gap: 12px;
  color: #555;
}

.follow-btn {
  margin-left: auto;
}

.tab-bar {
  margin-top: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.waterfall-wrapper {
  padding: 20px;
  display: flex;
  justify-content: center;
  background-color: transparent; /* 去掉背景颜色 */
}

.tab-bar {
  margin-top: 16px;
  border-bottom: 1px solid #f0f0f0;

  display: flex;
  justify-content: center;   /* 水平居中 */
}

/* 让 Tabs 本身宽度适配内容，而不是撑满 */
.tab-bar .ant-tabs-nav {
  margin: 0 auto !important;
}
</style>


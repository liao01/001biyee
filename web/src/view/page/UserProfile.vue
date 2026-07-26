<template>
  <a-card class="user-detail-card" bordered>
    <div class="user-header">
      <!-- 用户头像 -->
      <a-avatar
          :src="user.avatar ? baseUrl + user.avatar : defaultAvatar"
          :size="100"
          class="avatar"
      />
      <div class="user-info">
        <h2 class="username">{{ user.username }}</h2>
        <p class="bio">{{ user.bio || '这个人很神秘，什么也没留下~' }}</p>
      </div>
    </div>

    <a-divider />

    <a-descriptions title="个人信息" column="2" bordered>
      <a-descriptions-item label="用户名">
        {{ user.username }}
      </a-descriptions-item>
      <a-descriptions-item label="性别">
        {{ genderText(user.gender) }}
      </a-descriptions-item>
      <a-descriptions-item label="生日">
        {{ formatDate(user.birthday) }}
      </a-descriptions-item>
      <a-descriptions-item label="所在地">
        {{ user.location || '未填写' }}
      </a-descriptions-item>
    </a-descriptions>
  </a-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { message } from 'ant-design-vue'
import { BASE_URL } from "../../utils/baseUrl";

// 后端基础路径
const baseUrl = BASE_URL+'/lyw'


// 用户数据
const user = ref({})

// 生命周期加载数据
onMounted(async () => {
  try {
    const res = await axios.get(`${baseUrl}/web/UserProFile/findAllUser`)
    if (res.data.success && res.data.content.length > 0) {
      user.value = res.data.content[0]
    } else {
      message.warning('未找到用户信息')
    }
  } catch (error) {
    console.error(error)
    message.error('加载用户信息失败')
  }
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
</script>
<style scoped>
/* 容器大卡片美化 */
.user-detail-card {
  max-width: 800px;
  margin: 50px auto;
  border: none;
  border-radius: 20px;
  overflow: hidden; /* 确保背景不溢出 */
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.08);
  background: #fff;
  transition: transform 0.3s ease;
}

/* 模拟社交主页的顶部封面背景 */
.user-detail-card::before {
  content: "";
  display: block;
  height: 120px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  width: 100%;
}

/* 用户头部信息布局 */
.user-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: -50px; /* 头像向上移动，压在背景上 */
  padding-bottom: 24px;
  text-align: center;
}

/* 头像特效 */
.avatar {
  border: 4px solid #fff;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.15);
  background-color: #f9f9f9;
  transition: transform 0.3s ease;
}

.avatar:hover {
  transform: scale(1.05);
}

.user-info .username {
  margin: 15px 0 5px;
  font-size: 26px;
  font-weight: 700;
  color: #2c3e50;
  letter-spacing: 0.5px;
}

.user-info .bio {
  margin: 0;
  color: #95a5a6;
  font-size: 15px;
  font-style: italic;
  max-width: 80%;
  margin: 0 auto;
}

/* 调整分割线间距 */
:deep(.ant-divider) {
  margin: 0;
  border-color: #f0f0f0;
}

/* 深度自定义 a-descriptions 样式 */
:deep(.ant-descriptions) {
  padding: 30px;
  background: #fafafa;
}

:deep(.ant-descriptions-title) {
  font-size: 18px !important;
  font-weight: 600 !important;
  color: #333 !important;
  margin-bottom: 20px !important;
  display: flex;
  align-items: center;
}

/* 给标题加个小前缀修饰 */
:deep(.ant-descriptions-title)::before {
  content: "";
  width: 4px;
  height: 18px;
  background: #764ba2;
  margin-right: 10px;
  border-radius: 2px;
}

/* 单元格标签样式 */
:deep(.ant-descriptions-item-label) {
  background: #fff !important;
  color: #888 !important;
  font-weight: 500;
  width: 120px;
}

/* 单元格内容样式 */
:deep(.ant-descriptions-item-content) {
  background: #fff !important;
  color: #2c3e50 !important;
  font-size: 15px;
}

/* 响应式调整 */
@media (max-width: 576px) {
  .user-detail-card {
    margin: 20px;
    border-radius: 12px;
  }

  :deep(.ant-descriptions-item) {
    display: block;
  }
}
</style>

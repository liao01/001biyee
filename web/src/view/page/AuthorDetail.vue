<template>
    {{ userId }}
    <a-card class="user-detail-card" bordered>
      <div class="user-header">
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
        <a-descriptions-item label="用户名">{{ user.username }}</a-descriptions-item>
        <a-descriptions-item label="性别">{{ genderText(user.gender) }}</a-descriptions-item>
        <a-descriptions-item label="生日">{{ formatDate(user.birthday) }}</a-descriptions-item>
        <a-descriptions-item label="所在地">{{ user.location || '未填写' }}</a-descriptions-item>
      </a-descriptions>
    </a-card>
</template>


<script setup>
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import axios from 'axios'
import store from "../../store/index.js";

const baseUrl = 'http://localhost:8080/lyw'
// 用户数据
const user = ref({})
const userId = ref(null)

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
.user-detail-card {
  max-width: 700px;
  margin: 40px auto;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-radius: 16px;
  padding: 20px;
  background-color: #fff;
}

.user-header {
  display: flex;
  align-items: center;
  gap: 20px;
}

.avatar {
  border: 2px solid #f0f0f0;
}

.user-info .username {
  margin: 0;
  font-size: 22px;
  font-weight: bold;
}

.user-info .bio {
  margin-top: 6px;
  color: #888;
}
</style>


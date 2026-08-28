<template>
  <div class="travel-page profile-page">
    <ProfileHeader
      :profile="profileData"
      is-self
      @edit="goToEdit"
      @publish="goToPublish"
    />

    <section class="profile-page__details travel-panel">
      <header>
        <h2>个人资料</h2>
        <p>这些信息会帮助其他旅行者更了解你。</p>
      </header>
      <dl>
        <div>
          <dt>用户名</dt>
          <dd>{{ user.username || user.name || '未填写' }}</dd>
        </div>
        <div>
          <dt>性别</dt>
          <dd>{{ genderText(user.gender) }}</dd>
        </div>
        <div>
          <dt>生日</dt>
          <dd>{{ formatDate(user.birthday) }}</dd>
        </div>
        <div>
          <dt>所在地</dt>
          <dd>{{ user.location || '未填写' }}</dd>
        </div>
      </dl>
    </section>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, ref, onMounted } from 'vue'
import axios from 'axios'
import { message } from 'ant-design-vue'
import { BASE_URL } from "../../utils/baseUrl";
import ProfileHeader from '../../components/travel/ProfileHeader.vue'

// 后端基础路径
const baseUrl = BASE_URL+'/lyw'


// 用户数据
const user = ref({})
const appRouter = getCurrentInstance()?.appContext.config.globalProperties.$router
const profileData = computed(() => ({
  name: user.value.username || user.value.name || '旅行者',
  bio: user.value.bio,
  location: user.value.location,
  avatar: user.value.avatar ? baseUrl + user.value.avatar : '',
  stats: [],
}))
const goToEdit = () => appRouter?.push('/UserDetail')
const goToPublish = () => appRouter?.push('/uploadPost')

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
.profile-page {
  display: grid;
  gap: 22px;
}

.profile-page__details {
  padding: 28px 32px;
}

.profile-page__details header {
  border-bottom: 1px solid var(--travel-color-border);
  padding-bottom: 18px;
}

.profile-page__details h2 {
  font-size: 20px;
  margin: 0;
}

.profile-page__details header p {
  color: var(--travel-color-text-secondary);
  margin: 7px 0 0;
}

.profile-page__details dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
}

.profile-page__details dl div {
  border-bottom: 1px solid var(--travel-color-border);
  display: grid;
  gap: 12px;
  grid-template-columns: 90px 1fr;
  padding: 20px 0;
}

.profile-page__details dt {
  color: var(--travel-color-text-muted);
}

.profile-page__details dd {
  color: var(--travel-color-text);
  margin: 0;
}

@media (max-width: 699px) {
  .profile-page__details dl {
    grid-template-columns: 1fr;
  }
}
</style>

<template>
  <a-layout-header class="custom-header">
    <div class="header-content">
      <div class="header-left" @click="router.push('/')">
        <div class="logo-wrapper">
          <div class="logo-circle"><CompassFilled /></div>
          <span class="logo-text">旅分享</span>
        </div>
      </div>

      <div class="header-center">
        <div v-if="showSearch" :class="['search-group', { 'is-focused': isSearchFocused }]">
          <input
              v-model="searchText"
              class="search-input-inner"
              placeholder="搜索目的地、攻略..."
              @focus="isSearchFocused = true"
              @blur="isSearchFocused = false"
              @keyup.enter="onSearch(searchText)"
          />
          <button class="search-action-btn" @click="onSearch(searchText)">
            <SearchOutlined />
            <span>搜索</span>
          </button>
        </div>
      </div>

      <div class="header-right">
        <a-space :size="24">
          <div class="nav-links">
            <a-button type="text" class="nav-item">创作中心</a-button>
            <a-button type="text" class="nav-item">业务合作</a-button>
          </div>

          <a-divider type="vertical" />

          <a-dropdown :trigger="['click']" placement="bottomRight">
            <div class="user-trigger">
              <a-avatar :src="avatarUrl" :size="36">
                <template #icon><UserOutlined /></template>
              </a-avatar>
              <span class="username">{{ member.name || '未登录' }}</span>
              <DownOutlined class="drop-icon" />
            </div>
            <template #overlay>
              <a-menu class="user-dropdown-menu">
                <template v-if="member.name">
                  <a-menu-item @click="goToProfile"><UserOutlined /> 个人资料</a-menu-item>
                  <a-menu-item @click="goToEdit"><EditOutlined /> 修改资料</a-menu-item>
                  <a-menu-divider />
                  <a-menu-item @click="logout" danger><LogoutOutlined /> 退出登录</a-menu-item>
                </template>
                <template v-else>
                  <a-menu-item @click="showLogin"><LoginOutlined /> 立即登录</a-menu-item>
                </template>
              </a-menu>
            </template>
          </a-dropdown>
        </a-space>
      </div>
    </div>
    <TheLogin ref="loginRef" />
  </a-layout-header>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import {
  DownOutlined, SearchOutlined, UserOutlined,
  LogoutOutlined, EditOutlined, LoginOutlined, CompassFilled
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import axios from "axios"
import store from '../store/index.js'
import { useSearchStore } from '../store/search.js'
import TheLogin from './the-login.vue'

const router = useRouter()
const route = useRoute()
const searchStore = useSearchStore()

const loginRef = ref(null)
const searchText = ref('')
const isSearchFocused = ref(false)
const avatarUrl = ref('')
let heartTimer = null

const member = computed(() => store.state.member)
const showSearch = computed(() => route.path === '/CardList')

const fetchAvatar = async () => {
  if (!member.value?.name) return
  try {
    const { data } = await axios.get("http://localhost:8080/lyw/web/UserProFile/findAvatarUser")
    if (data.success) avatarUrl.value = `http://localhost:8080/lyw${data.content}`
  } catch (e) { console.error("Avatar error") }
}

const heart = () => axios.get('http://localhost:8080/lyw/web/member/heart').catch(() => {})
const showLogin = () => loginRef.value?.showModal()
const logout = () => { store.commit('clearMember'); message.success('已安全退出') }
const onSearch = (val) => { searchStore.setKeyword(val) }
const goToEdit = () => router.push('/UserDetail')
const goToProfile = () => router.push('/UserProfile')

watch(() => member.value?.name, (newName) => {
  if (newName) {
    fetchAvatar()
    if (!heartTimer) heartTimer = setInterval(heart, 60000)
  } else {
    avatarUrl.value = '';
    if (heartTimer) { clearInterval(heartTimer); heartTimer = null }
  }
}, { immediate: true })

onMounted(() => { window.showLogin = () => loginRef.value?.showModal() })
onBeforeUnmount(() => { if (heartTimer) clearInterval(heartTimer); delete window.showLogin })
</script>

<style scoped>
/* 基础布局 */
.custom-header {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid #f0f0f0;
  padding: 0 40px;
  height: 72px;
  position: sticky;
  top: 0;
  z-index: 1000;
  display: flex;
  justify-content: center;
}

.header-content {
  width: 100%;
  max-width: 1200px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* Logo 样式 */
.logo-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}
.logo-circle {
  width: 34px;
  height: 34px;
  background: #ff2442;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 18px;
  transform: rotate(-10deg);
}
.logo-text {
  font-size: 20px;
  font-weight: bold;
  color: #333;
}

/* 核心修复：手动对齐的搜索框 */
.search-group {
  display: flex;
  align-items: center;
  width: 100%;
  max-width: 400px;
  height: 42px;
  background: #f5f5f7;
  border-radius: 21px;
  padding: 2px; /* 留出内边距防止边框挤压 */
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid transparent;
}

.search-group.is-focused {
  max-width: 480px;
  background: #fff;
  border-color: #ff2442;
  box-shadow: 0 4px 12px rgba(255, 36, 66, 0.1);
}

.search-input-inner {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  padding: 0 20px;
  font-size: 14px;
  color: #333;
  height: 100%;
}

.search-action-btn {
  height: 38px; /* 略小于容器高度 */
  border-radius: 19px;
  background: #ff2442;
  color: white;
  border: none;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s;
}

.search-action-btn:hover {
  opacity: 0.9;
}

/* 用户区域 */
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 20px;
  transition: background 0.2s;
}
.user-trigger:hover {
  background: #f5f5f5;
}
.username {
  font-weight: 500;
  color: #444;
}
.nav-item { color: #666; font-weight: 500; }
.nav-item:hover { color: #ff2442 !important; }

@media (max-width: 800px) {
  .nav-links { display: none; }
}
</style>
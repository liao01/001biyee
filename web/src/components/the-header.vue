<template>
  <header class="travel-header">
    <button class="travel-header__brand" type="button" aria-label="返回发现页" @click="router.push('/')">
      <span class="travel-header__mark"><CompassFilled /></span>
      <span class="travel-header__brand-name">旅分享</span>
    </button>

    <div class="travel-header__search-zone">
      <label
        v-if="showSearch"
        :class="['travel-search', { 'is-focused': isSearchFocused }]"
      >
        <SearchOutlined class="travel-search__icon" />
        <input
          v-model="searchText"
          class="travel-search__input"
          placeholder="搜索目的地、攻略..."
          @focus="isSearchFocused = true"
          @blur="isSearchFocused = false"
          @keyup.enter="onSearch(searchText)"
        >
        <button class="travel-search__button" type="button" @click="onSearch(searchText)">搜索</button>
      </label>
    </div>

    <nav class="travel-header__actions" aria-label="快捷操作">
      <button class="travel-header__publish" type="button" @click="openPublish">
        <EditOutlined />
        <span>发布旅行</span>
      </button>

      <a-dropdown :trigger="['click']" placement="bottomRight">
        <button class="user-trigger" type="button" :aria-label="member.name ? '打开账户菜单' : '登录'">
          <a-avatar :src="avatarUrl" :size="36">
            <template #icon><UserOutlined /></template>
          </a-avatar>
          <span class="username">{{ member.name || '登录' }}</span>
          <DownOutlined class="drop-icon" />
        </button>
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
    </nav>
    <TheLogin ref="loginRef" />
  </header>
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
import { BASE_URL } from "../utils/baseUrl";

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
    const { data } = await axios.get(BASE_URL+"/lyw/web/UserProFile/findAvatarUser")
    if (data.success) avatarUrl.value = BASE_URL+`/lyw${data.content}`
  } catch (e) { console.error("Avatar error") }
}

const heart = () => axios.get(BASE_URL+'/lyw/web/member/heart').catch(() => {})
const showLogin = () => loginRef.value?.showModal()
const openPublish = () => {
  if (!member.value?.name) {
    showLogin()
    return
  }
  router.push('/uploadPost')
}
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
.travel-header {
  align-items: center;
  background: rgb(255 255 255 / 96%);
  border-bottom: 1px solid var(--travel-color-border);
  display: grid;
  gap: 24px;
  grid-template-columns: var(--travel-sidebar-width) minmax(260px, 640px) 1fr;
  height: var(--travel-header-height);
  padding: 0 28px 0 24px;
}

.travel-header__brand {
  align-items: center;
  background: transparent;
  border: 0;
  color: var(--travel-color-text);
  cursor: pointer;
  display: flex;
  gap: 11px;
  justify-self: start;
  padding: 0;
}

.travel-header__mark {
  align-items: center;
  background: var(--travel-color-brand);
  border-radius: 9px 9px 9px 3px;
  color: #fff;
  display: flex;
  font-size: 17px;
  height: 34px;
  justify-content: center;
  transform: rotate(-6deg);
  width: 34px;
}

.travel-header__brand-name {
  font-size: 21px;
  font-weight: 760;
  letter-spacing: -.04em;
}

.travel-header__search-zone {
  min-width: 0;
}

.travel-search {
  align-items: center;
  background: var(--travel-color-bg-subtle);
  border: 1px solid transparent;
  border-radius: 12px;
  display: flex;
  height: 42px;
  max-width: 600px;
  padding-left: 14px;
  transition: border-color var(--travel-transition), background var(--travel-transition);
}

.travel-search.is-focused {
  background: #fff;
  border-color: var(--travel-color-brand);
}

.travel-search__icon {
  color: var(--travel-color-text-muted);
  font-size: 16px;
}

.travel-search__input {
  background: transparent;
  border: 0;
  color: var(--travel-color-text);
  flex: 1;
  font-size: 14px;
  height: 100%;
  min-width: 0;
  outline: none;
  padding: 0 12px;
}

.travel-search__button {
  background: transparent;
  border: 0;
  color: var(--travel-color-brand);
  cursor: pointer;
  font-size: 14px;
  font-weight: 650;
  height: 100%;
  padding: 0 14px;
}

.travel-header__actions {
  align-items: center;
  display: flex;
  gap: 14px;
  justify-self: end;
}

.travel-header__publish {
  align-items: center;
  background: var(--travel-color-brand);
  border: 1px solid var(--travel-color-brand);
  border-radius: 10px;
  color: #fff;
  cursor: pointer;
  display: inline-flex;
  font-size: 14px;
  font-weight: 650;
  gap: 7px;
  height: 40px;
  padding: 0 16px;
}

.user-trigger {
  align-items: center;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 12px;
  color: var(--travel-color-text);
  cursor: pointer;
  display: flex;
  gap: 8px;
  padding: 3px 7px;
  transition: background var(--travel-transition), border-color var(--travel-transition);
}

.user-trigger:hover {
  background: var(--travel-color-bg-subtle);
  border-color: var(--travel-color-border);
}

.username {
  font-size: 14px;
  font-weight: 600;
}

.drop-icon {
  color: var(--travel-color-text-muted);
  font-size: 11px;
}

@media (max-width: 1099px) {
  .travel-header {
    grid-template-columns: 168px minmax(240px, 1fr) auto;
    padding-inline: 18px;
  }

  .travel-header__publish span,
  .username,
  .drop-icon {
    display: none;
  }
}

@media (max-width: 767px) {
  .travel-header {
    gap: 12px;
    grid-template-columns: auto minmax(0, 1fr) auto;
    height: 64px;
    padding: 0 14px;
  }

  .travel-header__brand-name {
    display: none;
  }

  .travel-header__search-zone {
    display: block;
  }

  .travel-search {
    border-radius: 10px;
    height: 38px;
  }

  .travel-search__button,
  .travel-header__publish {
    display: none;
  }
}
</style>

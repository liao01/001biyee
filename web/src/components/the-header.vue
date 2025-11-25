<template>
  <a-layout-header class="header">
    <!-- 左侧 Logo -->
    <div class="header-left">
      <div class="logo">旅分享</div>
    </div>

    <!-- 中间搜索框 -->
    <div class="header-center" v-if="showSearch">
      <a-input-search
          v-model:value="searchText"
          placeholder="搜索旅游攻略或目的地"
          enter-button
          @search="onSearch"
          style="max-width: 400px;"
      />
    </div>

    <!-- 登录弹窗 -->
    <TheLogin ref="loginRef" />

    <!-- 右侧导航 -->
    <div class="header-right">
      <a-space>
        <a-button type="link">创作中心</a-button>
        <a-button type="link">业务合作</a-button>

        <!-- Dropdown 始终渲染 Menu -->
        <a-dropdown>
          <a-button type="link">
            <a-avatar style="width: 40px;height: 40px" :src="avatarUrl" v-if="member.name!=null" />
            <span style="margin-left: 10px">
              {{ member.name || '未登录' }}
            </span>
            <DownOutlined />
          </a-button>

          <template #overlay>
            <a-menu>
              <!-- ✅ 当已登录时，显示“用户信息”相关功能 -->
              <a-menu-item
                  key="profile"
                  :disabled="!member.name"
                  @click="goToProfile"
              >
                个人资料
              </a-menu-item>

              <a-menu-item
                  key="edit"
                  :disabled="!member.name"
                  @click="goToEdit"
              >
                修改资料
              </a-menu-item>

              <a-menu-item
                  key="logout"
                  :disabled="!member.name"
                  @click="logout"
              >
                退出登录
              </a-menu-item>

              <!-- ✅ 未登录时显示 -->
              <a-menu-item
                  key="login"
                  :disabled="!!member.name"
                  @click="showLogin"
              >
                登录
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </a-space>
    </div>
  </a-layout-header>
</template>

<script setup>
import {ref, computed, onMounted, onBeforeUnmount, watch} from 'vue'
import { DownOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import store from '../store/index.js'
import TheLogin from './the-login.vue'
import { useSearchStore } from '../store/search.js'
import { useRoute } from 'vue-router'
import router from '../router/index.js'
import axios from "axios";

// 登录弹窗引用
const loginRef = ref(null)

// 搜索框输入
const searchText = ref('')
const searchStore = useSearchStore()

// 当前登录用户，直接依赖 Vuex
const member = computed(() => store.state.member)

const avatarUrl = ref('')

// 请求用户头像
const fetchAvatar = async () => {
  axios.get("http://localhost:8080/lyw/web/UserProFile/findAvatarUser").then(response => {
    const data = response.data;
    if (data.success) {
      avatarUrl.value = `http://localhost:8080/lyw${data.content}`
    } else {
      message.error(data.message)
    }
  })
}

// 打开登录弹窗
const showLogin = () => loginRef.value?.showModal()

// 退出登录
const logout = () => {
  store.commit('clearMember')
  message.success('退出成功')
}

// 全局暴露登录弹窗方法
onMounted(() => {
  window.showLogin = () => loginRef.value.show();
  if (member.value && member.value.name) fetchAvatar()
})

watch(member, (newVal) => {
  if (newVal && newVal.name) {
    fetchAvatar()
  } else {
    avatarUrl.value = ''
  }
})

onBeforeUnmount(() => {
  delete window.showLogin
})

// 搜索处理
const onSearch = (value) => {
  searchStore.setKeyword(value)
}

// 根据路由判断是否显示搜索框
const route = useRoute()
const showSearch = computed(() => route.path === '/CardList')

// 跳转到资料编辑页
const goToEdit = () => router.push('/UserDetail')
const goToProfile = () => router.push('/UserProfile')
</script>


<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  padding: 0 24px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}
.header-left .logo {
  font-size: 22px;
  font-weight: bold;
  color: #ff2442;
}
.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
}
</style>

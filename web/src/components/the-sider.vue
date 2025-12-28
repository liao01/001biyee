<template>
  <a-layout-sider
      v-model:collapsed="collapsed"
      collapsible
      width="240"
      class="custom-sider"
  >

    <a-menu
        v-model:selectedKeys="selectedKeys"
        v-model:openKeys="openKeys"
        mode="inline"
        class="custom-menu"
    >
      <a-menu-item key="/CardList" @click="handleMenuClick('/CardList')">
        <template #icon><HomeOutlined /></template>
        <span>发现</span>
      </a-menu-item>

      <a-sub-menu key="sub1">
        <template #icon><PlusSquareOutlined /></template>
        <template #title>关于我的</template>

        <a-menu-item key="/uploadPost" @click="handleMenuClick('/uploadPost', true)">
          <span>发布</span>
        </a-menu-item>
        <a-menu-item key="/Userfollow" @click="handleMenuClick('/Userfollow', true)">
          <span>粉丝数据</span>
        </a-menu-item>
        <a-menu-item key="/PostHistory" @click="handleMenuClick('/PostHistory', true)">
          <span>发布历史</span>
        </a-menu-item>
        <a-menu-item key="/CardlistView" @click="handleMenuClick('/CardlistView', true)">
          <span>浏览历史</span>
        </a-menu-item>
        <a-menu-item key="/FavoriteList" @click="handleMenuClick('/FavoriteList', true)">
          <span>我的收藏</span>
        </a-menu-item>
      </a-sub-menu>

      <a-menu-item key="/AI2" @click="handleMenuClick('/AI2', true)">
        <template #icon><RobotOutlined /></template>
        <span>旅游助手</span>
      </a-menu-item>

      <a-menu-item key="/Map" @click="handleMenuClick('/Map', true)">
        <template #icon><CompassOutlined /></template>
        <span>地图</span>
      </a-menu-item>
    </a-menu>

    <div class="login-btn-wrapper" v-if="!collapsed">
      <the-login ref="loginRef"></the-login>
    </div>
  </a-layout-sider>
</template>

<script setup>
import { ref, watch } from 'vue'
import {
  HomeOutlined, PlusSquareOutlined, RobotOutlined,
  CompassOutlined, UserOutlined
} from '@ant-design/icons-vue'
import TheLogin from "./the-login.vue"
import { useRouter } from "vue-router"
import { useStore } from "vuex"

const collapsed = ref(false)
const selectedKeys = ref(['/CardList'])
const openKeys = ref([])
const store = useStore()
const router = useRouter()

const handleMenuClick = (path, needLogin = false) => {
  if (needLogin && !store.state.member.token) {
    if (window.showLogin) window.showLogin()
    return
  }
  router.push(path)
}

watch(() => router.currentRoute.value.path, (newPath) => {
  selectedKeys.value = [newPath]
}, { immediate: true })
</script>

<style scoped>
/* 1. 基础容器：纯白 + 阴影 */
.custom-sider {
  background: #ffffff !important;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.05);
  height: 100vh;
  position: sticky;
  top: 0;
  z-index: 100;
}

/* 2. Logo 样式：与管理端完全一致 */
.sider-logo {
  height: 72px;
  display: flex;
  align-items: center;
  padding: 0 24px;
  cursor: pointer;
  overflow: hidden;
}

.logo-dot {
  width: 10px;
  height: 10px;
  background: #ff2442;
  border-radius: 50%;
  flex-shrink: 0;
  margin-right: 12px;
  box-shadow: 0 0 8px rgba(255, 36, 66, 0.5);
}

.logo-title {
  font-size: 16px;
  font-weight: 700;
  color: #333;
  white-space: nowrap;
}

/* 3. 菜单整体定制 */
.custom-menu {
  border-inline-end: none !important;
  padding: 8px !important;
}

/* 每一个项的圆角和间距 */
.custom-menu :deep(.ant-menu-item),
.custom-menu :deep(.ant-menu-submenu-title) {
  border-radius: 8px !important;
  margin-bottom: 4px !important;
  height: 44px !important;
  line-height: 44px !important;
}

/* 4. 交互：悬停与选中状态 */
/* 悬停态 */
.custom-menu :deep(.ant-menu-item:hover),
.custom-menu :deep(.ant-menu-submenu-title:hover) {
  color: #ff2442 !important;
  background: #fff1f0 !important;
}

/* 选中态：品牌红底+白字 */
.custom-menu :deep(.ant-menu-item-selected) {
  background: #ff2442 !important;
  color: #ffffff !important;
}

/* 5. 底部登录按钮 */
.login-btn-wrapper {
  position: absolute;
  bottom: 60px; /* 避开收起按钮 */
  width: 100%;
  padding: 0 16px;
  text-align: center;
}

/* 针对折叠状态的微调 */
:deep(.ant-layout-sider-collapsed) .sider-logo {
  padding: 0 35px;
}

:deep(.ant-layout-sider-trigger) {
  background: #fff !important;
  color: #999 !important;
  border-top: 1px solid #f0f0f0;
}
</style>
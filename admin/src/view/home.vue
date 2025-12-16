<template>
  <a-layout style="min-height: 100vh">
    <the-sider></the-sider>
    <a-layout>
      <a-layout-header class="app-header">
        <div class="header-content">
          <span class="welcome-message">欢迎回来，{{ user.loginName || '未登录' }}</span>&nbsp;&nbsp;
          <router-link to="/login">
            退出登录
          </router-link>
        </div>
      </a-layout-header>

      <a-layout-content style="margin: 0 16px">
        <a-breadcrumb style="margin: 16px 0">
        </a-breadcrumb>

        <div class="content-wrapper">
          <router-view />
        </div>
      </a-layout-content>

      <a-layout-footer class="app-footer">
        © {{ new Date().getFullYear() }} [您的公司/项目名称]. All Rights Reserved.
      </a-layout-footer>
    </a-layout>
  </a-layout>
</template>

<script setup>
import TheSider from "../components/the-sider.vue";
import {computed} from "vue";
import store from "../store/index.js";

// 当前登录用户，直接依赖 Vuex
const user = computed(() => store.state.user)
</script>

<style scoped>
/* 保持上一个回答中的所有美化样式，以确保页面美观度 */
.app-header {
  background: #fff;
  padding: 0 24px;
  height: 64px;
  line-height: 64px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  z-index: 10;
}

.header-content {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.welcome-message {
  font-size: 14px;
  color: #555;
}

.content-wrapper {
  padding: 24px;
  background: #fff;
  min-height: 360px;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.app-footer {
  text-align: center;
  color: #8c8c8c;
  padding: 16px 50px;
  font-size: 13px;
}

.site-layout .site-layout-background {
  background: #fff;
}
[data-theme='dark'] .site-layout .site-layout-background {
  background: #141414;
}
</style>
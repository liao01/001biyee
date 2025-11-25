<template>
  <a-layout style="min-height: 100vh;">
    <!-- 固定头部 -->
    <a-layout-header
        style="position: fixed; top: 0; left: 0; right: 0; z-index: 100; height: 64px; background: #fff; padding: 0;"
    >
      <the-header />
    </a-layout-header>

    <!-- 整体主体区域（含侧边栏 + 内容） -->
    <a-layout style="margin-top: 64px;">
      <!-- 固定侧边栏 -->
      <a-layout-sider
          width="200"
          style="position: fixed; left: 0; top: 64px; bottom: 0; background: #fff;"
      >
        <the-sider />
      </a-layout-sider>

      <!-- 内容区 -->
      <a-layout-content
          style="margin-left: 200px; padding: 24px; min-height: calc(100vh - 70px); overflow-y: auto; background: #f5f5f5;"
      >
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup>
import TheHeader from "../components/the-header.vue";
import TheSider from "../components/the-sider.vue";
</script>

<style scoped>
.header-left .logo {
  font-size: 22px;
  font-weight: bold;
  color: #ff2442;
}
</style>

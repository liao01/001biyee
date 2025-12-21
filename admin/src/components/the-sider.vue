<template>
  <a-layout-sider v-model:collapsed="collapsed" collapsible>
    <div class="logo" />
    <a-menu v-model:selectedKeys="selectedKeys" theme="dark" mode="inline">
      <a-menu-item key="/home/dashboard">
        <router-link to="/home/dashboard">
          <pie-chart-outlined />
          <span>数据概况</span>
        </router-link>
      </a-menu-item>
      <a-sub-menu key="2">
        <template #title>
          <desktop-outlined />
          <span>旅游地图管理</span>
        </template>
        <a-menu-item key="1">
          <router-link to="/home/uploadMap">添加地图详细</router-link>
        </a-menu-item>
        <a-menu-item key="2">option2</a-menu-item>
        <a-menu-item key="3">option3</a-menu-item>
        <a-menu-item key="4">option4</a-menu-item>
      </a-sub-menu>
      <a-menu-item key="/home/UserManagement">
        <router-link to="/home/UserManagement">
          <user-outlined />
          <span>用户管理</span>
        </router-link>
      </a-menu-item>
    </a-menu>
  </a-layout-sider>
</template>

<script  setup>
import {computed, ref} from 'vue';
import {useRoute} from "vue-router";

const collapsed = ref(false)

// 当前路由
const route = useRoute()

// ✅ 菜单选中项 = 当前路由 path
const selectedKeys = computed(() => [route.path])
</script>

<style scoped>
.logo {
  height: 32px;
  margin: 16px;
  background: rgba(255, 255, 255, 0.3);
}

.site-layout .site-layout-background {
  background: #fff;
}
[data-theme='dark'] .site-layout .site-layout-background {
  background: #141414;
}
</style>
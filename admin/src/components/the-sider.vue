<template>
  <a-layout-sider
      v-model:collapsed="collapsed"
      collapsible
      class="custom-sider"
      width="240"
  >
    <div class="sider-logo" @click="$router.push('/')">
      <div class="logo-dot"></div>
      <span v-if="!collapsed" class="logo-title">旅分享 · 管理</span>
    </div>

    <a-menu
        v-model:selectedKeys="selectedKeys"
        theme="light"
        mode="inline"
        class="custom-menu"
    >
      <a-menu-item key="/home/dashboard">
        <router-link to="/home/dashboard">
          <PieChartOutlined />
          <span>数据概况</span>
        </router-link>
      </a-menu-item>

      <a-sub-menu key="sub1">
        <template #title>
          <span>
            <DesktopOutlined />
            <span>旅游地图管理</span>
          </span>
        </template>
        <a-menu-item key="/home/uploadMap">
          <router-link to="/home/uploadMap">添加地图详细</router-link>
        </a-menu-item>
        <a-menu-item key="/home/LocationAdmin">
          <router-link to="/home/LocationAdmin">查看地图详细</router-link>
        </a-menu-item>
      </a-sub-menu>

      <a-menu-item key="/home/UserManagement">
        <router-link to="/home/UserManagement">
          <UserOutlined />
          <span>用户管理</span>
        </router-link>
      </a-menu-item>
    </a-menu>
  </a-layout-sider>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRoute } from "vue-router";
import {
  PieChartOutlined,
  DesktopOutlined,
  UserOutlined
} from '@ant-design/icons-vue';

const collapsed = ref(false)
const route = useRoute()

// 菜单选中项绑定当前路由
const selectedKeys = computed(() => [route.path])
</script>

<style scoped>
/* 侧边栏容器样式 */
.custom-sider {
  background: #fff !important;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.05);
  height: 100vh;
  position: sticky;
  top: 0;
  left: 0;
  z-index: 100;
}

/* Logo 样式升级 */
.sider-logo {
  height: 72px; /* 与 Header 同高 */
  display: flex;
  align-items: center;
  padding: 0 24px;
  cursor: pointer;
  overflow: hidden;
  transition: all 0.3s;
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
  letter-spacing: 0.5px;
}

/* 菜单项深度定制 */
.custom-menu {
  border-inline-end: none !important; /* 移除右侧默认边框 */
  padding: 8px;
}

/* 每一个菜单项的样式 */
.custom-menu :deep(.ant-menu-item),
.custom-menu :deep(.ant-menu-submenu-title) {
  border-radius: 8px !important;
  margin-bottom: 4px !important;
  height: 48px !important;
  line-height: 48px !important;
  transition: all 0.3s;
}

/* 悬停效果 */
.custom-menu :deep(.ant-menu-item:hover),
.custom-menu :deep(.ant-menu-submenu-title:hover) {
  color: #ff2442 !important;
  background: #fff1f0 !important;
}

/* 选中项高亮样式 */
.custom-menu :deep(.ant-menu-item-selected) {
  background: #ff2442 !important;
  color: #fff !important;
}

.custom-menu :deep(.ant-menu-item-selected a) {
  color: #fff !important;
}

/* 调整图标间距 */
.ant-menu-item .anticon,
.ant-menu-submenu-title .anticon {
  font-size: 16px;
}

/* 折叠后的样式优化 */
:deep(.ant-layout-sider-collapsed) .sider-logo {
  padding: 0 32px;
}

/* 底部收起按钮美化 */
:deep(.ant-layout-sider-trigger) {
  background: #fff !important;
  color: #999 !important;
  border-top: 1px solid #f0f0f0;
}
:deep(.ant-layout-sider-trigger:hover) {
  color: #ff2442 !important;
}
</style>
<template>
  <!-- ① 最外层铺满屏幕 -->
  <a-layout class="root-layout">
    <!-- 左侧菜单 -->
    <a-layout-sider v-model:collapsed="collapsed" collapsible>
      <div class="logo" />
      <a-menu
          v-model:selectedKeys="selectedKeys"
          theme="dark"
          mode="inline"
      >
        <a-menu-item key="1">
          <pie-chart-outlined />
          <span>Option 1</span>
        </a-menu-item>

        <a-menu-item key="2">
          <desktop-outlined />
          <span>Option 2</span>
        </a-menu-item>

        <a-sub-menu key="sub1">
          <template #title>
            <span>
              <user-outlined />
              <span>User</span>
            </span>
          </template>
          <a-menu-item key="3">Tom</a-menu-item>
          <a-menu-item key="4">Bill</a-menu-item>
          <a-menu-item key="5">Alex</a-menu-item>
        </a-sub-menu>

        <a-sub-menu key="sub2">
          <template #title>
            <span>
              <team-outlined />
              <span>Team</span>
            </span>
          </template>
          <a-menu-item key="6">Team 1</a-menu-item>
          <a-menu-item key="8">Team 2</a-menu-item>
        </a-sub-menu>

        <a-menu-item key="9">
          <file-outlined />
          <span>File</span>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>

    <!-- 右侧主体 -->
    <a-layout class="right-layout">
      <a-layout-header class="header" />

      <a-layout-content class="content">
        <a-breadcrumb class="breadcrumb">
          <a-breadcrumb-item>User</a-breadcrumb-item>
          <a-breadcrumb-item>Bill</a-breadcrumb-item>
        </a-breadcrumb>

        <div class="content-box">
          Bill is a cat.
        </div>
      </a-layout-content>

      <a-layout-footer class="footer">
        Ant Design ©2018 Created by Ant UED
      </a-layout-footer>
    </a-layout>
  </a-layout>
</template>

<script setup>
import { ref } from 'vue'

const collapsed = ref(false)
const selectedKeys = ref(['1'])
</script>

<style scoped>
/* === 核心：整体铺满 === */
.root-layout {
  min-height: 100vh;
  width: 100%;
}

/* 右侧布局必须占满 */
.right-layout {
  width: 100%;
}

/* logo */
.logo {
  height: 32px;
  margin: 16px;
  background: rgba(255, 255, 255, 0.3);
}

/* header */
.header {
  background: #fff;
  padding: 0;
  height: 64px;
}

/* content 必须横向 + 纵向都可撑 */
.content {
  margin: 16px;
  flex: 1;
  width: 100%;
  display: flex;
  flex-direction: column;
}

/* 面包屑 */
.breadcrumb {
  margin-bottom: 16px;
}

/* 白色内容区 */
.content-box {
  flex: 1;
  width: 100%;
  padding: 24px;
  background: #fff;
}

/* footer */
.footer {
  width: 100%;
  text-align: center;
}
</style>

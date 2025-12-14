<template>
  <!-- ① 最外层铺满屏幕 -->
  <a-layout class="root-layout">

    <TheSider></TheSider>

    <!-- 右侧主体 -->
    <a-layout class="right-layout">
      <a-layout-header class="header"/>
      <a-layout-content class="content">
          <div :style="{ padding: '24px', background: '#fff' }">
            Bill is a cat.
          </div>
      </a-layout-content>

      <the-footer></the-footer>
    </a-layout>
  </a-layout>
</template>

<script setup>
import { ref } from 'vue'
import TheSider from '../components/the-sider.vue'
import TheFooter from "../components/the-footer.vue";

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

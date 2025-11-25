<template>
  <a-layout-sider width="200" style="background: #fff">
    <a-menu
        v-model:selectedKeys="selectedKeys"
        v-model:openKeys="openKeys"
        mode="inline"
        :style="{ height: '100%', borderRight: 0 }"
    >
      <a-menu-item key="/CardList">
        <router-link to="/CardList">
          <HomeOutlined />
          <span class="nav-text">发现</span>
        </router-link>
      </a-menu-item>
      <a-sub-menu key="sub1">
        <template #title>
          <PlusSquareOutlined />
          <span class="nav-text">关于我的</span>
        </template>

        <!-- 发布 -->
        <a-menu-item key="/uploadPost" @click="handleMenuClick('/uploadPost', true)">
          <router-link to="">
            发布
          </router-link>
        </a-menu-item>

        <!-- 粉丝数据 -->
        <a-menu-item key="/Userfollow"  @click="handleMenuClick('/Userfollow', true)">
          <router-link to="">
            粉丝数据
          </router-link>
        </a-menu-item>
        <a-menu-item key="/PostHistory"  @click="handleMenuClick('/PostHistory', true)">
          <router-link to="">
            发布历史
          </router-link>
        </a-menu-item>
        <a-menu-item key="/CardlistView"  @click="handleMenuClick('/CardlistView', true)">
          <router-link to="">
            浏览历史
          </router-link>
        </a-menu-item>
        <a-menu-item key="/FavoriteList"  @click="handleMenuClick('/FavoriteList', true)">
          <router-link to="">
            收藏
          </router-link>
        </a-menu-item>
      </a-sub-menu>
      <a-menu-item key="/AI"  @click="handleMenuClick('/AI', true)">
        <RobotOutlined />
        <span class="nav-text">你的旅游助手</span>
      </a-menu-item>
      <a-menu-item key="/Map" @click="handleMenuClick('/Map', true)">
        <CompassOutlined />
        <span class="nav-text">地图</span>
      </a-menu-item>


      <div class="login-btn-wrapper">
        <the-login ref="loginRef"></the-login>
      </div>
    </a-menu>
  </a-layout-sider>
</template>

<script setup>
import {ref, watch} from 'vue'
import { HomeOutlined, PlusSquareOutlined, BellOutlined } from '@ant-design/icons-vue'
import TheLogin from "./the-login.vue"
import {useRouter} from "vue-router";
import {useStore} from "vuex";

const selectedKeys = ref(['/CardList'])
const openKeys = ref([])
const store = useStore();
let router = useRouter();

// 点击菜单项时检查登录状态
const handleMenuClick = (path, needLogin = false) => {
  if (needLogin && !store.state.member.token) {
    // 未登录 -> 通过全局方法唤起 Header 登录框
    if (window.showLogin) {
      window.showLogin()
    } else {
      console.warn("Header 登录框未加载完成")
    }
    return
  }
  router.push(path)
}

watch(() => router.currentRoute.value.path,(newValue,oldValue)=>{
  console.log('watch',newValue,oldValue);
  selectedKeys.value = [];
  selectedKeys.value.push(newValue);
},{immediate:true})
</script>

<style scoped>
.login-btn-wrapper {
  padding: 16px;
  text-align: center;
}

</style>

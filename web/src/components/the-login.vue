<template>
  <a-modal
      v-model:open="open"
      :title="null"
      :footer="null"
  >
    <!-- 登录表单 -->
    <div v-if="activeTab === 'login'">
      <login @login-success="closeModal"></login>
      <a @click="activeTab = 'register'">去注册</a> |
      <a @click="activeTab = 'forgot'">忘记密码？</a>
    </div>

    <!-- 注册表单 -->
    <div v-if="activeTab === 'register'">
      <register :activeTab="activeTab" @switchTab="tab => activeTab = tab"></register>
      <a @click="activeTab = 'login'">已有账号？去登录</a>
    </div>

    <!-- 忘记密码表单 -->
    <div v-if="activeTab === 'forgot'">
      <forgot :activeTab="activeTab" @switchTab="tab => activeTab = tab"></forgot>
      <a @click="activeTab = 'login'">返回登录</a>
    </div>
  </a-modal>
</template>

<script setup>
import { ref, computed } from 'vue'
import Login from "../view/login.vue";
import Register from "../view/register.vue";
import Forgot from "../view/forgot.vue";

const open = ref(false)
const activeTab = ref('login')

const modalTitle = computed(() => {
  if (activeTab.value === 'login') return '用户登录'
  if (activeTab.value === 'register') return '用户注册'
  if (activeTab.value === 'forgot') return '找回密码'
})

const showModal = (tab = 'login') => {
  activeTab.value = tab
  open.value = true
}

const closeModal = () => {
  open.value = false
}
const show = () => {
  open.value = true
}


// 暴露给父组件调用
defineExpose({
  show,
  showModal,
  closeModal
})
</script>

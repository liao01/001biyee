<template>
  <div class="login-page">
    <a-card class="login-card" :bordered="false">
      <h2 class="login-title">用户登录</h2>

      <a-form
          :model="loginUser"
          @finish="login"
      >
        <a-form-item
            name="loginName"
            :rules="[{ required: true, message: '请输入用户名' }]"
        >
          <a-input
              v-model:value="loginUser.loginName"
              placeholder="用户名"
              size="large"
          >
            <template #prefix>
              <UserOutlined />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item
            name="password"
            :rules="[{ required: true, message: '请输入密码' }]"
        >
          <a-input-password
              v-model:value="loginUser.password"
              placeholder="密码"
              size="large"
          >
            <template #prefix>
              <LockOutlined />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item
            name="imageCode"
            :rules="[{ required: true, message: '请输入图片验证码' }]"
        >
          <a-input
              v-model:value="loginUser.imageCode"
              placeholder="图片验证码"
              size="large"
          >
            <template #prefix>
              <SafetyOutlined />
            </template>
            <template #suffix>
              <img
                  class="captcha-img"
                  v-show="!!imageCodeSrc"
                  :src="imageCodeSrc"
                  @click="loadImageCode"
              />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item>
          <a-button
              type="primary"
              block
              size="large"
              html-type="submit"
          >
            登录
          </a-button>
        </a-form-item>
        <!-- 底部链接 -->
        <div class="login-footer">
          <span @click="goRegister" class="link">注册账号</span>
          <span @click="goForgotPassword" class="link">忘记密码？</span>
        </div>
      </a-form>
    </a-card>
  </div>
</template>

<script setup>
import { ref } from "vue";
import axios from "axios";
import { message } from "ant-design-vue";
import store from "../store/index.js";
import router from "../router/index.js";

const loginUser = ref({
  loginName: '',
  password: '',
  imageCode: ''
})

const login = () => {
  axios.post("http://localhost:8080/lyw/admin/member/login", {
    loginName: loginUser.value.loginName,
    password: hexMd5Key(loginUser.value.password),
    imageCode: loginUser.value.imageCode,
    imageCodeToken: imageCodeToken.value
  }).then(res => {
    const data = res.data
    if (data.success) {
      message.success("登录成功")
      store.commit("setMember", data.content)
      // 页面跳转
      window.location.href = '/home'
    } else {
      message.error(data.message)
      loadImageCode()
    }
  })
}

// -------- 图形验证码 --------
const imageCodeToken = ref()
const imageCodeSrc = ref()

const loadImageCode = () => {
  loginUser.value.imageCode = ''
  imageCodeToken.value = Tool.uuid(8)
  imageCodeSrc.value =
      'http://localhost:8080/lyw/admin/kaptcha/image-code/' + imageCodeToken.value
}

loadImageCode()

// 底部跳转
const goRegister = () => {
  router.push('/register')
}
const goForgotPassword = () => {
  router.push('/forgot-password')
}
</script>

<style scoped>
.login-page {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #1677ff, #69b1ff);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 380px;
  padding: 20px 10px;
  border-radius: 12px;
}

.login-title {
  text-align: center;
  margin-bottom: 24px;
  font-weight: 600;
}

.captcha-img {
  height: 32px;
  cursor: pointer;
}

/* 底部链接 */
.login-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  font-size: 14px;
}

.link {
  color: #1677ff;
  cursor: pointer;
  user-select: none;
}
.link:hover {
  text-decoration: underline;
}
</style>

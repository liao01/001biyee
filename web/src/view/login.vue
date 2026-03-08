<template>
  <div class="login-container">
    <div class="login-header">
      <h2>欢迎登录</h2>
      <p>请登录您的账号以继续</p>
    </div>

    <a-form
        :model="loginMember"
        name="basic"
        layout="vertical"
        @finish="login"
        class="custom-form"
    >
      <a-form-item
          name="mobile"
          class="form-item"
          :rules="[{ required: true, message: '请输入邮箱' }]"
      >
        <a-input
            placeholder="邮箱"
            v-model:value="loginMember.mobile"
            size="large"
            class="custom-input"
        >
          <template #prefix>
            <MobileOutlined class="icon-style"/>
          </template>
        </a-input>
      </a-form-item>

      <a-form-item
          name="password"
          class="form-item"
          :rules="[{ required: true, message: '请输入密码' }]"
      >
        <a-input-password
            placeholder="密码"
            v-model:value="loginMember.password"
            size="large"
            class="custom-input"
        >
          <template #prefix>
            <LockOutlined class="icon-style"/>
          </template>
        </a-input-password>
      </a-form-item>

      <a-form-item
          name="imageCode"
          class="form-item"
          :rules="[{ required: true, message: '请输入图片验证码', trigger: 'blur' }]"
      >
        <a-input
            v-model:value="loginMember.imageCode"
            placeholder="图片验证码"
            size="large"
            class="custom-input"
        >
          <template #prefix>
            <SafetyOutlined class="icon-style"/>
          </template>
          <template #suffix>
            <div class="captcha-wrapper">
              <img
                  v-show="!!imageCodeSrc"
                  :src="imageCodeSrc"
                  alt="验证码"
                  @click="loadImageCode()"
                  title="点击刷新验证码"
              />
            </div>
          </template>
        </a-input>
      </a-form-item>

      <a-form-item class="form-item-btn">
        <a-button
            type="primary"
            block
            html-type="submit"
            class="login-btn"
            size="large"
        >
          立即登录
        </a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup>
import { ref } from "vue";
import axios from "axios";
import { message } from "ant-design-vue";
import store from "../store/index.js";

const loginMember = ref({
  mobile: '',
  password: '',
  imageCode:''
})

const emit = defineEmits(['login-success'])

const login = values => {
  console.log('开始登录', values);
  axios.post("http://localhost:8080/lyw/web/member/login", {
    mobile: loginMember.value.mobile,
    password: hexMd5Key(loginMember.value.password),
    imageCode:  loginMember.value.imageCode,
    imageCodeToken: imageCodeToken.value
  }).then(response => {
    const data = response.data;
    if (data.success) {
      message.success("登录成功!");
      store.commit("setMember",data.content)
      // 🔹触发事件通知父组件关闭模态框
      emit('login-success')
      console.log("登录返回数据", data.content);
    } else {
      message.error(data.message)
    }
  })
}
// ----------- 图形验证码 --------------------
const imageCodeToken = ref();
const imageCodeSrc = ref();
/**
 * 加载图形验证码
 */
const loadImageCode = () => {
  loginMember.value.imageCode = "";
  imageCodeToken.value = Tool.uuid(8);
  imageCodeSrc.value = 'http://localhost:8080/lyw/web/kaptcha/image-code/' + imageCodeToken.value;
};
loadImageCode();
</script>

<style scoped>
/* 容器居中与样式 */
.login-container {
  max-width: 400px;
  margin: 0 auto;
  padding: 20px;
}

/* 标题样式 */
.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: #1f1f1f;
  margin-bottom: 8px;
}

.login-header p {
  color: #8c8c8c;
  font-size: 14px;
}

/* 输入框间距与圆角 */
.form-item {
  margin-bottom: 20px;
}

.icon-style {
  color: #bfbfbf;
  margin-right: 8px;
  font-size: 16px;
}

.custom-input :deep(.ant-input-affix-wrapper) {
  border-radius: 8px;
  padding: 8px 12px;
  transition: all 0.3s;
}

/* 验证码图片样式 */
.captcha-wrapper {
  height: 32px;
  display: flex;
  align-items: center;
  cursor: pointer;
}

.captcha-wrapper img {
  height: 100%;
  border-radius: 4px;
  transition: opacity 0.2s;
}

.captcha-wrapper img:hover {
  opacity: 0.8;
}

/* 登录按钮美化 */
.login-btn {
  height: 45px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  background: linear-gradient(90deg, #1890ff 0%, #40a9ff 100%);
  border: none;
  box-shadow: 0 4px 10px rgba(24, 144, 255, 0.3);
  margin-top: 10px;
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 15px rgba(24, 144, 255, 0.4);
}

.login-btn:active {
  transform: translateY(0);
}
</style>

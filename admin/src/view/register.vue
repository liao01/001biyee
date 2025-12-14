<template>
  <div class="login-page">
    <a-card class="login-card" :bordered="false">
      <h2 class="login-title">重置密码</h2>

      <a-form :model="resetMember" @finish="reset">
        <!-- 手机号 -->
        <a-form-item
            name="mobile"
            :rules="[
              { required: true, message: '请输入手机号', trigger: 'blur' },
              { pattern: /^\d{11}$/, message: '手机号为11位数字', trigger: 'blur' }
            ]"
        >
          <a-input v-model:value="resetMember.mobile" placeholder="手机号" size="large">
            <template #prefix>
              <MobileOutlined />
            </template>
          </a-input>
        </a-form-item>

        <!-- 图形验证码 -->
        <a-form-item
            name="imageCode"
            :rules="[{ required: true, message: '请输入图片验证码', trigger: 'blur' }]"
        >
          <a-input v-model:value="resetMember.imageCode" placeholder="图片验证码" size="large">
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

        <!-- 短信验证码 -->
        <a-form-item
            name="code"
            :rules="[{ required: true, message: '请输入短信验证码', trigger: 'blur' }]"
        >
          <a-input-search
              placeholder="短信验证码"
              v-model:value="resetMember.code"
              :enter-button="sendText"
              @search="sendResetSmsCode"
              :loading="sendBtnLoading"
          >
            <template #prefix>
              <MessageOutlined />
            </template>
          </a-input-search>
        </a-form-item>

        <!-- 新密码 -->
        <a-form-item
            name="passwordOri"
            :rules="[
              { required: true, message: '请输入密码', trigger: 'blur' },
              { pattern: /^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z]{6,20}$/, message: '密码包含数字和英文，长度6-20', trigger: 'blur' }
            ]"
        >
          <a-input-password
              v-model:value="resetMember.passwordOri"
              placeholder="新密码"
              size="large"
          >
            <template #prefix>
              <LockOutlined />
            </template>
          </a-input-password>
        </a-form-item>

        <!-- 确认密码 -->
        <a-form-item
            name="passwordConfirm"
            :rules="[{ required: true, message: '请输入确认密码', trigger: 'blur' }]"
        >
          <a-input-password
              v-model:value="resetMember.passwordConfirm"
              placeholder="确认密码"
              size="large"
          >
            <template #prefix>
              <CheckCircleOutlined />
            </template>
          </a-input-password>
        </a-form-item>

        <!-- 提交按钮 -->
        <a-form-item>
          <a-button type="primary" block size="large" html-type="submit">
            重置密码
          </a-button>
        </a-form-item>
      </a-form>

      <!-- 底部链接 -->
      <div class="login-footer">
        <span @click="goLogin" class="link">返回登录</span>
      </div>
    </a-card>
  </div>
</template>

<script setup>
import { ref } from "vue";
import axios from "axios";
import { message } from "ant-design-vue";
import { useRouter } from "vue-router";

const router = useRouter();

const resetMember = ref({
  mobile: '',
  password: '',
  passwordOri: '',
  passwordConfirm: '',
  code: '',
  imageCode: ''
});

// 重置密码提交
const reset = () => {
  if (resetMember.value.passwordOri !== resetMember.value.passwordConfirm) {
    message.error("密码和确认密码不一致");
    return;
  }
  resetMember.value.password = resetMember.value.passwordOri;
  axios.post("http://localhost:8080/lyw/admin/member/reset", {
    mobile: resetMember.value.mobile,
    code: resetMember.value.code,
    password: hexMd5Key(resetMember.value.password)
  }).then(res => {
    const data = res.data;
    if (data.success) {
      message.success("重置密码成功!");
      router.push('/login'); // 成功跳转登录页
    } else {
      message.error(data.message);
      loadImageCode();
    }
  });
}

// 图形验证码
const imageCodeToken = ref();
const imageCodeSrc = ref();
const loadImageCode = () => {
  resetMember.value.imageCode = '';
  imageCodeToken.value = Tool.uuid(8);
  imageCodeSrc.value = 'http://localhost:8080/lyw/admin/kaptcha/image-code/' + imageCodeToken.value;
};
loadImageCode();

// 短信验证码
const sendBtnLoading = ref(false);
const sendText = ref("获取验证码");
const COUNTDOWN = 5;
let countdown = ref(COUNTDOWN);

const setTime = () => {
  if (countdown.value === 0) {
    sendText.value = "获取验证码";
    countdown.value = COUNTDOWN;
    sendBtnLoading.value = false;
    return;
  }
  sendText.value = `重新发送(${countdown.value})`;
  sendBtnLoading.value = true;
  countdown.value--;
  setTimeout(setTime, 1000);
}

const sendResetSmsCode = () => {
  if (!resetMember.value.mobile) {
    message.error("请输入手机号");
    return;
  }
  sendBtnLoading.value = true;
  axios.post("http://localhost:8080/lyw/admin/sms-code/send-for-reset", {
    mobile: resetMember.value.mobile,
    imageCode: resetMember.value.imageCode,
    imageCodeToken: imageCodeToken.value
  }).then(res => {
    const data = res.data;
    if (data.success) {
      message.success("短信发送成功!");
      setTime();
    } else {
      sendBtnLoading.value = false;
      message.error(data.message);
      loadImageCode();
    }
  });
}

// 跳转登录
const goLogin = () => {
  router.push('/login');
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
  justify-content: center;
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

<template>
  <div class="reset-container">
    <div class="reset-header">
      <h2>找回密码</h2>
      <p>验证手机号以安全重置您的密码</p>
    </div>

    <a-form
        :model="resetMember"
        name="basic"
        layout="vertical"
        @finish="reset"
        class="custom-form"
    >
      <a-form-item
          name="mobile" class="form-item"
          :rules="[
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]"
      >
        <a-input v-model:value="resetMember.mobile" placeholder="邮箱" size="large" class="custom-input">
          <template #prefix>
            <MobileOutlined class="icon-style"/>
          </template>
        </a-input>
      </a-form-item>

      <a-form-item name="imageCode" class="form-item"
                   :rules="[{ required: true, message: '请输入图片验证码', trigger: 'blur' }]">
        <a-input v-model:value="resetMember.imageCode" placeholder="图形验证码" size="large" class="custom-input">
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
                  title="点击刷新"
              />
            </div>
          </template>
        </a-input>
      </a-form-item>

      <a-form-item name="code" class="form-item"
                   :rules="[{ required: true, message: '请输入验证码', trigger: 'blur' }]">
        <a-input-search
            placeholder="验证码"
            v-model:value="resetMember.code"
            :enter-button="sendText"
            size="large"
            @search="sendResetSmsCode"
            :loading="sendBtnLoading"
            class="custom-search"
        >
          <template #prefix>
            <MessageOutlined class="icon-style"/>
          </template>
        </a-input-search>
      </a-form-item>

      <a-form-item
          name="passwordOri"
          class="form-item"
          :rules="[
            { required: true, message: '请输入密码', trigger: 'blur' },
            { pattern: /^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z]{6,20}$/, message: '密码需包含数字和英文(6-20位)', trigger: 'blur' }
          ]"
      >
        <a-input-password
            v-model:value="resetMember.passwordOri"
            placeholder="新密码"
            size="large"
            class="custom-input"
        >
          <template #prefix>
            <LockOutlined class="icon-style"/>
          </template>
        </a-input-password>
      </a-form-item>

      <a-form-item
          name="passwordConfirm"
          class="form-item"
          :rules="[{ required: true, message: '请输入确认密码' }]"
      >
        <a-input-password
            v-model:value="resetMember.passwordConfirm"
            placeholder="确认新密码"
            size="large"
            class="custom-input"
        >
          <template #prefix>
            <CheckCircleOutlined class="icon-style"/>
          </template>
        </a-input-password>
      </a-form-item>

      <a-form-item class="form-item-btn">
        <a-button type="primary" block html-type="submit" class="reset-btn" size="large">
          确认重置密码
        </a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup>
import {ref} from 'vue';
import axios from "axios";
import {message} from "ant-design-vue";
const resetMember = ref(
    {
      mobile:'',
      password:'',
      passwordOri:'',
      passwordConfirm:'',
      code:'',
      imageCode:''
    }
);

const props = defineProps({
  activeTab : String
})

const emit = defineEmits(['switchTab'])
const reset = values =>{
  console.log('开始重置密码',values);
  if (resetMember.value.passwordOri !== resetMember.value.passwordConfirm){
    message.error("密码和确认密码不一致")
    return;
  }
  resetMember.value.password = resetMember.value.passwordOri
  axios.post("http://localhost:8080/lyw/web/member/reset",{
    mobile: resetMember.value.mobile,
    code : resetMember.value.code,
    password: hexMd5Key(resetMember.value.password)
  }).then(response =>{
    let data = response.data;
    if (data.success){
      message.success("重置密码成功!");
      emit('switchTab', 'login');
    }
    else{
      message.error(data.message)
    }
  })
}

//短信验证码
const sendBtnLoading = ref(false);
const sendText = ref("获取验证码");
const COUNTDOWN = 5;
let countdown = ref(COUNTDOWN);
const setTime = () =>{
  if (countdown.value === 0){
    sendText.value = "获取验证码";
    countdown.value = COUNTDOWN;
    sendBtnLoading.value = false;
    return;
  }else{
    sendText.value = "重新发送("+countdown.value +")";
    sendBtnLoading.value = true;
    countdown.value--;}
  setTimeout(function (){
    setTime();
  },1000)
}

const sendResetSmsCode = () =>{
  console.log('发送验证码');
  sendBtnLoading.value = true;
  axios.post("http://localhost:8080/lyw/web/sms-code/send-for-reset",{
    mobile: resetMember.value.mobile,
    imageCode:  resetMember.value.imageCode,
    imageCodeToken: imageCodeToken.value
  }).then(response =>{
    let data = response.data;
    if (data.success){
      setTime();
      message.success("短信发送成功!");
    }
    else{
      sendBtnLoading.value = false;
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
  resetMember.value.imageCode = "";
  imageCodeToken.value = Tool.uuid(8);
  imageCodeSrc.value = 'http://localhost:8080/lyw/web/kaptcha/image-code/' + imageCodeToken.value;
};
loadImageCode();

</script>


<style scoped>
/* 保持与登录注册一致的容器样式 */
.reset-container {
  max-width: 400px;
  margin: 0 auto;
  padding: 10px 20px;
}

.reset-header {
  text-align: center;
  margin-bottom: 25px;
}

.reset-header h2 {
  font-size: 22px;
  font-weight: 600;
  color: #1f1f1f;
  margin-bottom: 6px;
}

.reset-header p {
  color: #8c8c8c;
  font-size: 14px;
}

.form-item {
  margin-bottom: 18px;
}

.icon-style {
  color: #bfbfbf;
  margin-right: 8px;
  font-size: 16px;
}

/* 统一输入框样式 */
.custom-input :deep(.ant-input-affix-wrapper),
.custom-search :deep(.ant-input-affix-wrapper) {
  border-radius: 8px;
  padding: 8px 12px;
}

/* 短信验证码搜素框特殊处理：按钮圆角 */
.custom-search :deep(.ant-input-group-addon .ant-btn) {
  border-radius: 0 8px 8px 0;
  height: 40px;
}

/* 图形验证码 */
.captcha-wrapper {
  height: 30px;
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

/* 重置按钮 - 使用深色调强调安全性 */
.reset-btn {
  height: 45px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  background: linear-gradient(90deg, #1d39c4 0%, #2f54eb 100%);
  border: none;
  box-shadow: 0 4px 10px rgba(47, 84, 235, 0.2);
  margin-top: 5px;
}

.reset-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 15px rgba(47, 84, 235, 0.3);
}

.reset-btn:active {
  transform: translateY(0);
}
</style>
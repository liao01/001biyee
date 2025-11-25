<template>
  <a-form
      :model="loginMember"
      name="basic"
      :wrapper-col="{ span: 24 }"
      @finish="login"
  >
    <a-form-item
        name="mobile"
        class="form-item"
        :rules="[{ required: true, message: '请输入手机号' }]"
    >
      <a-input placeholder="手机号" v-model:value="loginMember.mobile" size="large">
        <template #prefix>
          <MobileOutlined style="margin-left: 15px"/>
        </template>
      </a-input>
    </a-form-item>

    <a-form-item
        name="password"
        class="form-item"
        :rules="[{ required: true, message: '请输入密码' }]"
    >
      <a-input-password placeholder="密码" v-model:value="loginMember.password" size="large">
        <template #prefix>
          <LockOutlined style="margin-left: 15px"/>
        </template>
      </a-input-password>
    </a-form-item>

    <a-form-item name="imageCode" class="form-item"
                 :rules="[{ required: true, message: '请输入图片验证码', trigger: 'blur' }]">
      <a-input v-model:value="loginMember.imageCode" placeholder="图片验证码">
        <template #prefix>
          <SafetyOutlined style="margin-left: 15px"/>
        </template>
        <template #suffix>
          <img v-show="!!imageCodeSrc" :src="imageCodeSrc" alt="验证码" v-on:click="loadImageCode()"/>
        </template>
      </a-input>
    </a-form-item>

    <a-form-item class="form-item">
      <a-button type="primary" block html-type="submit" class="login-btn" size="large">
        登录
      </a-button>
    </a-form-item>
  </a-form>
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

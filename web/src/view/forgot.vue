<template>
  <a-form
      :model="resetMember"
      name="basic"
      :wrapper-col="{ span: 24 }"
      @finish="reset"
  >
    <a-form-item
        name="mobile" class="form-item"
        :rules="[{ required: true, message: '请输入手机号', trigger: 'blur' }, { pattern: /^\d{11}$/, message: '手机号为11位数字', trigger: 'blur' }]"
    >
      <a-input v-model:value="resetMember.mobile" placeholder="手机号" size="large">
        <template #prefix>
          <MobileOutlined style="margin-left: 15px"/>
        </template>
      </a-input>
    </a-form-item>

    <a-form-item name="imageCode" class="form-item"
                 :rules="[{ required: true, message: '请输入图片验证码', trigger: 'blur' }]">
      <a-input v-model:value="resetMember.imageCode" placeholder="图片验证码">
        <template #prefix>
          <SafetyOutlined style="margin-left: 15px"/>
        </template>
        <template #suffix>
          <img v-show="!!imageCodeSrc" :src="imageCodeSrc" alt="验证码" v-on:click="loadImageCode()"/>
        </template>
      </a-input>
    </a-form-item>

    <a-form-item name="code" class="form-item"
                 :rules="[{ required: true, message: '请输入短信验证码', trigger: 'blur' }]">
      <a-input-search
          placeholder="短信验证码"
          v-model:value="resetMember.code"
          :enter-button="sendText"
          @search="sendResetSmsCode"
          :loading="sendBtnLoading"
      >
        <template #prefix>
          <MessageOutlined style="margin-left: 15px"/>
        </template>
      </a-input-search>
    </a-form-item>



    <a-form-item
        name="passwordOri"
        class="form-item"
        :rules="[
    { required: true, message: '请输入密码', trigger: 'blur' },
    { pattern: /^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z]{6,20}$/, message: '密码包含数字和英文，长度6-20', trigger: 'blur' }
  ]"
    >
      <a-input-password
          v-model:value="resetMember.passwordOri"
          placeholder="密码"
          size="large"
      >
        <template #prefix>
          <LockOutlined style="margin-left: 15px"/>
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
          placeholder="确认密码"
          size="large"
      >
        <template #prefix>
          <CheckCircleOutlined style="margin-left: 15px"/>
        </template>
      </a-input-password>
    </a-form-item>


    <a-form-item class="form-item">
      <a-button type="primary" block html-type="submit" class="reset-btn" size="large">
        重置密码
      </a-button>
    </a-form-item>
  </a-form>
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
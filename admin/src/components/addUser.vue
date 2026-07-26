<template>
  <a-modal
      v-model:open="visible"
      title="账号注册"
      :footer="null"
      centered
      :width="400"
      wrap-class-name="register-modal-custom"
  >
    <div class="form-container">
      <div class="form-header">
        <h3>创建您的账号</h3>
        <p>开启您的智能化管理之旅</p>
      </div>

      <a-form
          :model="addUserAdmin"
          name="basic"
          layout="vertical"
          @finish="onFinish"
      >
        <a-form-item
            name="loginName"
            :rules="[{ required: true, message: '请输入用户名', trigger: 'blur' }]"
        >
          <a-input
              v-model:value="addUserAdmin.loginName"
              placeholder="用户名"
              size="large"
              allow-clear
          >
            <template #prefix><UserOutlined class="icon-color" /></template>
          </a-input>
        </a-form-item>

        <a-form-item
            name="passwordOri"
            :rules="[
            { required: true, message: '请输入密码', trigger: 'blur' },
            { pattern: /^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z]{6,20}$/, message: '密码需包含数字和英文，6-20位', trigger: 'blur' }
          ]"
        >
          <a-input-password
              v-model:value="addUserAdmin.passwordOri"
              placeholder="设置密码"
              size="large"
          >
            <template #prefix><LockOutlined class="icon-color" /></template>
          </a-input-password>
        </a-form-item>

        <a-form-item
            name="passwordConfirm"
            :rules="[{ required: true, message: '请再次输入密码确认' }]"
        >
          <a-input-password
              v-model:value="addUserAdmin.passwordConfirm"
              placeholder="确认密码"
              size="large"
          >
            <template #prefix><CheckCircleOutlined class="icon-color" /></template>
          </a-input-password>
        </a-form-item>

        <a-form-item
            name="imageCode"
            :rules="[{ required: true, message: '请输入验证码', trigger: 'blur' }]"
        >
          <div class="captcha-wrapper">
            <a-input
                v-model:value="addUserAdmin.imageCode"
                placeholder="图形验证码"
                size="large"
                class="captcha-input"
            >
              <template #prefix><SafetyOutlined class="icon-color" /></template>
            </a-input>
            <div class="captcha-img-box" @click="loadImageCode">
              <img v-if="imageCodeSrc" :src="imageCodeSrc" alt="验证码" />
              <div v-else class="loading-placeholder">加载中...</div>
            </div>
          </div>
        </a-form-item>

        <a-form-item class="form-action">
          <a-button
              type="primary"
              block
              html-type="submit"
              size="large"
              class="submit-btn"
              :loading="loading"
          >
            立即注册
          </a-button>
        </a-form-item>
      </a-form>
    </div>
  </a-modal>
</template>

<script setup>
import { computed, ref } from 'vue';
import axios from "axios";
import { message } from "ant-design-vue";
import { UserOutlined, LockOutlined, CheckCircleOutlined, SafetyOutlined } from '@ant-design/icons-vue';
import store from "../store/index.js";
import { BASE_URL } from "../utils/baseUrl";

const props = defineProps({
  open: { type: Boolean, default: false }
});

const emit = defineEmits(['update:open', 'success']);

const loading = ref(false);
const addUserAdmin = ref({
  loginName: '',
  passwordOri: '',
  passwordConfirm: '',
  imageCode: ''
});

const visible = computed({
  get: () => props.open,
  set: (val) => emit('update:open', val)
});

const onFinish = async () => {
  if (addUserAdmin.value.passwordOri !== addUserAdmin.value.passwordConfirm) {
    message.error("两次输入的密码不一致");
    return;
  }

  loading.value = true;
  try {
    const res = await axios.post(BASE_URL+"/lyw/admin/member/register", {
      loginName: addUserAdmin.value.loginName,
      password: hexMd5Key(addUserAdmin.value.passwordOri),
      imageCode: addUserAdmin.value.imageCode,
      imageCodeToken: imageCodeToken.value
    });

    if (res.data.success) {
      message.success("注册成功!");
      store.commit("setMember", res.data.content);
      emit('success');
      visible.value = false;
    } else {
      message.error(res.data.message);
      loadImageCode(); // 失败时刷新验证码
    }
  } catch (err) {
    message.error("网络请求失败");
  } finally {
    loading.value = false;
  }
};

// ----------- 图形验证码 --------------------
const imageCodeToken = ref("");
const imageCodeSrc = ref("");

const loadImageCode = () => {
  addUserAdmin.value.imageCode = "";
  // 假设 Tool 是已定义的工具类
  imageCodeToken.value = typeof Tool !== 'undefined' ? Tool.uuid(8) : Math.random().toString(36).substring(2, 10);
  imageCodeSrc.value = BASE_URL+`/lyw/web/kaptcha/image-code/${imageCodeToken.value}`;
};

loadImageCode();
</script>

<style scoped>
/* 头部样式 */
.form-header {
  text-align: center;
  margin-bottom: 24px;
}
.logo {
  width: 48px;
  margin-bottom: 12px;
}
.form-header h3 {
  font-size: 20px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}
.form-header p {
  color: #8c8c8c;
  font-size: 14px;
}

/* 验证码布局 */
.captcha-wrapper {
  display: flex;
  gap: 12px;
  align-items: center;
}
.captcha-input {
  flex: 1;
}
.captcha-img-box {
  width: 120px;
  height: 40px;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid #d9d9d9;
  transition: all 0.3s;
}
.captcha-img-box:hover {
  border-color: #40a9ff;
  opacity: 0.8;
}
.captcha-img-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.loading-placeholder {
  font-size: 12px;
  color: #ccc;
  text-align: center;
  line-height: 38px;
}

/* 图标颜色 */
.icon-color {
  color: #bfbfbf;
}

/* 按钮与页脚 */
.submit-btn {
  height: 45px;
  font-size: 16px;
  border-radius: 6px;
  background: linear-gradient(90deg, #1890ff 0%, #40a9ff 100%);
  border: none;
  box-shadow: 0 4px 10px rgba(24, 144, 255, 0.3);
}
.submit-btn:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}
.footer-links {
  text-align: center;
  margin-top: 16px;
  font-size: 14px;
  color: #8c8c8c;
}

/* 覆盖 Ant Design Modal 默认内边距 */
:deep(.ant-modal-body) {
  padding: 32px 24px !important;
}
</style>
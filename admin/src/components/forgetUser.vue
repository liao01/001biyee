<template>
  <a-modal
      v-model:open="visible"
      title="安全重置 - 账户密码"
      :footer="null"
      centered
      :width="420"
      class="reset-modal-custom"
  >
    <div class="form-container">
      <div class="reset-header">
        <p class="reset-subtitle">请为 ID 为 <span class="user-id">#{{ userId }}</span> 的用户设置新密码</p>
      </div>

      <a-form
          :model="forgetUserAdmin"
          name="forget_form"
          layout="vertical"
          @finish="onFinish"
          class="styled-form"
      >
        <a-form-item
            name="passwordOri"
            label="新密码"
            :rules="[
            { required: true, message: '请输入新密码', trigger: 'blur' },
            { pattern: /^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z]{6,20}$/, message: '需包含数字和英文，6-20位', trigger: 'blur' }
          ]"
        >
          <a-input-password
              v-model:value="forgetUserAdmin.passwordOri"
              placeholder="请输入 6-20 位新密码"
              size="large"
          >
            <template #prefix><LockOutlined class="icon-color" /></template>
          </a-input-password>
        </a-form-item>

        <a-form-item
            name="passwordConfirm"
            label="确认新密码"
            :rules="[{ required: true, message: '请再次输入密码以确认' }]"
        >
          <a-input-password
              v-model:value="forgetUserAdmin.passwordConfirm"
              placeholder="请再次输入新密码"
              size="large"
          >
            <template #prefix><CheckCircleOutlined class="icon-color" /></template>
          </a-input-password>
        </a-form-item>

        <a-form-item
            name="imageCode"
            label="身份验证"
            :rules="[{ required: true, message: '请输入验证码', trigger: 'blur' }]"
        >
          <div class="captcha-wrapper">
            <a-input
                v-model:value="forgetUserAdmin.imageCode"
                placeholder="图形验证码"
                size="large"
                class="captcha-input"
            >
              <template #prefix><SafetyOutlined class="icon-color" /></template>
            </a-input>
            <div class="captcha-img-box" @click="loadImageCode" title="点击刷新验证码">
              <img v-if="imageCodeSrc" :src="imageCodeSrc" alt="验证码" />
              <div v-else class="loading-placeholder">
                <a-spin size="small" />
              </div>
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
            确认重置密码
          </a-button>
          <a-button
              block
              class="cancel-btn"
              @click="visible = false"
              v-show="!loading"
          >
            取消
          </a-button>
        </a-form-item>
      </a-form>
    </div>
  </a-modal>
</template>

<script setup>
import {computed, ref, watch} from 'vue';
import axios from "axios";
import { message } from "ant-design-vue";
import { UserOutlined, LockOutlined, CheckCircleOutlined, SafetyOutlined } from '@ant-design/icons-vue';
import store from "../store/index.js";

const props = defineProps({
  open: { type: Boolean, default: false },
  userId: { type: [String, Number], required: true }
})

const emit = defineEmits(['update:open', 'success']);

const loading = ref(false);
const forgetUserAdmin = ref({
  id:'',
  loginName: '',
  passwordOri: '',
  passwordConfirm: '',
  imageCode: ''
});

const visible = computed({
  get: () => props.open,
  set: (val) => emit('update:open', val)
});

watch(
    () => props.open,
    (val) => {
      if (val) {
        // 弹窗打开时，把父组件传来的 id 赋值
        forgetUserAdmin.value.id = props.userId
      }
    }
)

const onFinish = async () => {
  if (forgetUserAdmin.value.passwordOri !== forgetUserAdmin.value.passwordConfirm) {
    message.error("两次输入的密码不一致");
    return;
  }

  loading.value = true;
  try {
    const res = await axios.post("http://localhost:8080/lyw/admin/member/forget", {
      id:forgetUserAdmin.value.id,
      loginName: forgetUserAdmin.value.loginName,
      password: hexMd5Key(forgetUserAdmin.value.passwordOri),
      imageCode: forgetUserAdmin.value.imageCode,
      imageCodeToken: imageCodeToken.value
    });

    if (res.data.success) {
      message.success("重置成功!");
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
  forgetUserAdmin.value.imageCode = "";
  // 假设 Tool 是已定义的工具类
  imageCodeToken.value = typeof Tool !== 'undefined' ? Tool.uuid(8) : Math.random().toString(36).substring(2, 10);
  imageCodeSrc.value = `http://localhost:8080/lyw/web/kaptcha/image-code/${imageCodeToken.value}`;
};

loadImageCode();
</script>

<style scoped>
/* 深度修改 Modal 标题样式 */
:deep(.ant-modal-header) {
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 16px;
}

:deep(.ant-modal-title) {
  font-weight: 600;
  color: #1f1f1f;
}

/* 顶部提示 */
.reset-header {
  margin-bottom: 20px;
}
.reset-subtitle {
  color: #8c8c8c;
  font-size: 13px;
  margin: 0;
}
.user-id {
  color: #1890ff;
  font-weight: bold;
  background: #e6f7ff;
  padding: 2px 6px;
  border-radius: 4px;
}

/* 表单 Label 样式优化 */
:deep(.ant-form-item-label > label) {
  font-weight: 500;
  color: #434343;
}

/* 输入框聚焦背景色 */
:deep(.ant-input-affix-wrapper-lg) {
  transition: all 0.3s;
  border-color: #d9d9d9;
}
:deep(.ant-input-affix-wrapper-focused) {
  background-color: #fff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
}

/* 验证码样式 */
.captcha-wrapper {
  display: flex;
  gap: 12px;
  align-items: center;
}
.captcha-img-box {
  width: 120px;
  height: 40px;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid #d9d9d9;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  background: #fafafa;
  display: flex;
  align-items: center;
  justify-content: center;
}
.captcha-img-box:hover {
  border-color: #1890ff;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.captcha-img-box img {
  width: 100%;
  height: 100%;
  object-fit: fill; /* 确保验证码填满容器 */
}

/* 图标颜色 */
.icon-color {
  color: #bfbfbf;
}

/* 提交按钮 - 渐变风格 */
.submit-btn {
  height: 45px;
  font-size: 16px;
  font-weight: 500;
  border-radius: 6px;
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.35);
  margin-top: 8px;
}

.submit-btn:hover {
  opacity: 0.9;
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(24, 144, 255, 0.45);
}

.submit-btn:active {
  transform: translateY(0);
}

/* 取消按钮 */
.cancel-btn {
  margin-top: 12px;
  height: 40px;
  border-radius: 6px;
  color: #8c8c8c;
  border-color: #d9d9d9;
}
.cancel-btn:hover {
  color: #595959;
  border-color: #bfbfbf;
  background: #fafafa;
}

/* 去除默认内边距 */
:deep(.ant-modal-body) {
  padding: 24px 32px 32px 32px !important;
}

/* 表单项间距调整 */
.ant-form-item {
  margin-bottom: 20px;
}
.form-action {
  margin-bottom: 0;
  margin-top: 32px;
}
</style>
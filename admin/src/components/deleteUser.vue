<template>
  <a-modal
      v-model:open="visible"
      title="危险操作确认"
      :ok-text="confirmText"
      cancel-text="再想想"
      ok-type="danger"
      :confirm-loading="loading"
      @ok="onOk"
      @cancel="onCancel"
      destroy-on-close
      :width="440"
      centered
  >
    <div class="delete-confirm-container">
      <a-alert
          type="error"
          show-icon
          class="mb-24"
      >
        <template #message>
          <span class="alert-title">不可逆操作</span>
        </template>
        <template #description>
          您正在尝试删除用户账号，该操作执行后将无法找回。
        </template>
      </a-alert>

      <div class="user-info-card">
        <div class="info-label">待删除对象</div>
        <div class="info-value">
          <Avatar class="user-avatar">{{ user?.loginName?.charAt(0).toUpperCase() }}</Avatar>
          <span class="user-name">{{ user?.loginName }}</span>
          <span class="user-id">ID: {{ user?.id }}</span>
        </div>
      </div>

      <div class="verify-section">
        <div class="section-title">安全验证</div>
        <div class="captcha-wrapper">
          <a-input
              v-model:value="form.imageCode"
              placeholder="输入验证码确认删除"
              size="large"
              class="captcha-input"
              allow-clear
          >
            <template #prefix>
              <SafetyOutlined class="icon-muted" />
            </template>
          </a-input>

          <div class="captcha-img-box" @click="loadImageCode" title="点击切换验证码">
            <img v-if="imageCodeSrc" :src="imageCodeSrc" alt="验证码" />
            <div v-else class="loading-box">
              <LoadingOutlined spin />
            </div>
          </div>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { message, Avatar } from 'ant-design-vue'
import { SafetyOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import axios from 'axios'

const props = defineProps({
  open: Boolean,
  user: Object
})

const emit = defineEmits(['update:open', 'success'])

const visible = computed({
  get: () => props.open,
  set: v => emit('update:open', v)
})

const form = ref({ imageCode: '' })
const loading = ref(false)
const confirmText = ref('确认彻底删除')

// ----------- 图形验证码 --------------------
const imageCodeToken = ref('')
const imageCodeSrc = ref('')

const loadImageCode = () => {
  form.value.imageCode = ''
  // 尽量使用外部工具类，此处保持逻辑一致
  imageCodeToken.value = typeof Tool !== 'undefined'
      ? Tool.uuid(8)
      : Math.random().toString(36).substring(2, 10)

  imageCodeSrc.value = `http://localhost:8080/lyw/admin/kaptcha/image-code/${imageCodeToken.value}`
}

watch(visible, (v) => {
  if (v) {
    loadImageCode()
    loading.value = false
  }
})

const onOk = async () => {
  if (!form.value.imageCode) {
    message.warning('请先输入图形验证码')
    return
  }

  loading.value = true
  try {
    const res = await axios.post('http://localhost:8080/lyw/admin/member/delete', {
      id: props.user.id,
      imageCode: form.value.imageCode,
      imageCodeToken: imageCodeToken.value
    })

    if (res.data.success) {
      message.success('用户已成功移除')
      emit('success')
      visible.value = false
    } else {
      message.error(res.data.message || '删除失败')
      loadImageCode() // 失败自动刷新验证码
    }
  } catch (err) {
    message.error('网络请求异常，请稍后再试')
  } finally {
    loading.value = false
  }
}

const onCancel = () => {
  visible.value = false
}
</script>

<style scoped>
.mb-24 {
  margin-bottom: 24px;
}

.alert-title {
  font-weight: 600;
}

/* 用户详情卡片样式 */
.user-info-card {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 24px;
}

.info-label {
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 8px;
  text-transform: uppercase;
}

.info-value {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  background-color: #ff4d4f;
  vertical-align: middle;
}

.user-name {
  font-size: 16px;
  font-weight: 600;
  color: #262626;
}

.user-id {
  font-size: 12px;
  color: #bfbfbf;
}

/* 验证码样式优化 */
.section-title {
  font-size: 14px;
  color: #262626;
  margin-bottom: 12px;
  font-weight: 500;
}

.captcha-wrapper {
  display: flex;
  gap: 12px;
}

.captcha-input {
  flex: 1;
}

.captcha-img-box {
  width: 120px;
  height: 40px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
}

.captcha-img-box:hover {
  border-color: #ff4d4f;
  opacity: 0.8;
}

.captcha-img-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.loading-box {
  color: #bfbfbf;
  font-size: 18px;
}

.icon-muted {
  color: #bfbfbf;
}

/* 深度选择器覆盖Modal默认按钮边距 */
:deep(.ant-modal-footer) {
  padding: 16px 24px !important;
  background: #fafafa;
  border-top: 1px solid #f0f0f0;
  border-radius: 0 0 8px 8px;
}
</style>
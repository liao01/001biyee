<template>
  <div class="travel-page edit-profile-page">
    <header class="travel-page__header">
      <div>
        <h1 class="travel-page__title">编辑个人资料</h1>
        <p class="travel-page__subtitle">完善旅行者身份，让交流更有温度。</p>
      </div>
    </header>
    <section class="travel-panel edit-profile-page__form">
    <a-form
        layout="vertical"
        :model="form"
        :rules="rules"
        ref="formRef"
        @finish="onSubmit"
    >
      <!-- 用户名 -->
      <a-form-item label="用户名" name="username" required>
        <a-input
            v-model:value="form.username"
            placeholder="请输入用户名"
            allow-clear
        />
      </a-form-item>
      <!-- 头像上传 -->
      <a-form-item label="头像" name="avatar" required>
        <a-upload
            list-type="picture-card"
            :show-upload-list="false"
            :before-upload="beforeUpload"
            @change="handleAvatarChange"
        >
          <img
              v-if="form.avatar"
              :src="form.avatar"
              alt="avatar"
              class="edit-profile-page__avatar"
          >
          <div v-else>
            <plus-outlined />
            <div style="margin-top: 8px;">上传头像</div>
          </div>
        </a-upload>
      </a-form-item>

      <!-- 性别 -->
      <a-form-item label="性别" name="gender">
        <a-radio-group v-model:value="form.gender">
          <a-radio :value="1">男</a-radio>
          <a-radio :value="2">女</a-radio>
          <a-radio :value="0">保密</a-radio>
        </a-radio-group>
      </a-form-item>

      <!-- 生日 -->
      <a-form-item label="生日" name="birthday" required>
        <a-date-picker
            v-model:value="form.birthday"
            class="edit-profile-page__date"
            placeholder="请选择生日"
        />
      </a-form-item>

      <!-- 简介 -->
      <a-form-item label="个人简介" name="bio">
        <a-textarea
            v-model:value="form.bio"
            rows="3"
            placeholder="请输入个人简介"
            allow-clear
        />
      </a-form-item>

      <!-- 所在地 -->
      <a-form-item label="所在地" name="location">
        <a-input
            v-model:value="form.location"
            placeholder="请输入所在地"
            allow-clear
        />
      </a-form-item>



      <!-- 提交按钮 -->
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit">保存资料</a-button>
          <a-button @click="onReset">重置</a-button>
        </a-space>
      </a-form-item>
    </a-form>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import axios from 'axios'
import { BASE_URL } from "../../utils/baseUrl";

const formRef = ref(null)
const form = reactive({
  avatar: '',        // Base64 字符串
  gender: 0,
  birthday: null,
  bio: '',
  location: '',
  username:''
})

// 表单校验规则
const rules = {
  avatar: [{ required: true, message: '头像不能为空' }],
  birthday: [{ required: true, message: '生日不能为空' }]
}

// 上传前校验
const beforeUpload = (file) => {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    message.error('只能上传图片！')
    return false
  }
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isLt2M) {
    message.error('图片不能超过2MB！')
    return false
  }
  return true
}

// Base64 上传处理
const handleAvatarChange = (info) => {
  const file = info.file.originFileObj
  const reader = new FileReader()
  reader.onload = (e) => {
    form.avatar = e.target.result  // Base64 字符串
  }
  reader.readAsDataURL(file)
}

// 提交表单
const onSubmit = async () => {
  try {
    await formRef.value.validate()
    // 日期转换成 yyyy-MM-dd 格式
    const submitData = {
      ...form,
      birthday: form.birthday ? form.birthday.format?.('YYYY-MM-DD') || form.birthday : null
    }

    const token = localStorage.getItem('token') || ''  // 可替换为你的 token 获取方式

    const res = await axios.post(BASE_URL+
        '/lyw/web/UserProFile/save',
        submitData,
        { headers: { token } }
    )

    if (res.data.success) {
      message.success('资料保存成功！')
    } else {
      message.error(res.data.message || '保存失败')
    }
  } catch (err) {
    console.error(err)
    message.error('表单验证或提交失败')
  }
}

// 重置表单
const onReset = () => {
  Object.assign(form, {
    avatar: '',
    gender: 0,
    birthday: null,
    bio: '',
    location: ''
  })
}
</script>

<style scoped>
.edit-profile-page__form {
  max-width: 760px;
  padding: 30px 34px;
}

.edit-profile-page__avatar,
.ant-upload-select {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  object-fit: cover;
}

.edit-profile-page__date {
  width: 100%;
}

:deep(.ant-input),
:deep(.ant-picker),
:deep(.ant-input-affix-wrapper) {
  border-radius: 10px;
  min-height: 42px;
}

:deep(.ant-btn-primary) {
  background: var(--travel-color-brand);
  border-color: var(--travel-color-brand);
}
</style>

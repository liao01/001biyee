<template>
  <div class="admin-container">
    <a-card title="新增景点" :bordered="false" class="form-card">
      <template #extra>
        <span class="sub-title">管理后台 - 景点录入</span>
      </template>

      <a-form
          :model="form"
          layout="vertical"
          @finish="handleSubmit"
          autocomplete="off"
      >
        <a-form-item
            label="景点名称"
            name="name"
            :rules="[{ required: true, message: '景点名称不能为空' }]"
        >
          <a-input v-model:value="form.name" placeholder="请输入景点全称">
            <template #prefix><shop-outlined /></template>
          </a-input>
        </a-form-item>

        <a-form-item
            label="地理位置"
            name="formattedAddress"
            :rules="[{ required: true, message: '详细地址不能为空' }]"
        >
          <a-input v-model:value="form.formattedAddress" placeholder="例如：北京市东城区景山前街4号">
            <template #prefix><environment-outlined /></template>
          </a-input>
        </a-form-item>

        <a-form-item label="景点介绍" name="description">
          <a-textarea
              v-model:value="form.description"
              :rows="6"
              placeholder="请在此输入景点的历史背景、开放时间、建筑特色等..."
              show-count
              :maxlength="1000"
          />
        </a-form-item>

        <a-form-item label="上传实景图片">
          <a-upload
              v-model:file-list="fileList"
              list-type="picture-card"
              :before-upload="() => false"
              multiple
          >
            <div v-if="fileList.length < 9">
              <plus-outlined />
              <div style="margin-top: 8px">上传图片</div>
            </div>
          </a-upload>
          <div class="upload-hint">支持多选，建议比例 4:3，单张不超过 5MB</div>
        </a-form-item>

        <a-divider />

        <a-form-item class="form-actions">
          <a-button
              type="primary"
              html-type="submit"
              block
              size="large"
              :loading="submitting"
          >
            保存并发布
          </a-button>
          <a-button block style="margin-top: 12px" @click="handleReset">
            重置
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import { PlusOutlined, EnvironmentOutlined, ShopOutlined } from '@ant-design/icons-vue';
import axios from 'axios';

// 响应式状态
const submitting = ref(false);
const fileList = ref([]);
const form = ref({
  name: '',
  formattedAddress: '',
  description: ''
});

// 重置表单
const handleReset = () => {
  form.value = { name: '', formattedAddress: '', description: '' };
  fileList.value = [];
};

// 表单提交
async function handleSubmit() {
  submitting.value = true;
  try {
    const formData = new FormData();
    formData.append('name', form.value.name);
    formData.append('formattedAddress', form.value.formattedAddress);
    formData.append('description', form.value.description || '');

    // 提取 a-upload 的原始文件对象
    fileList.value.forEach(file => {
      if (file.originFileObj) {
        formData.append('files', file.originFileObj);
      }
    });

    const res =  await axios.post(
        'http://localhost:8080/lyw/admin/location/save',
        formData,
        { headers: { 'Content-Type': 'multipart/form-data' } }
    );

    if (!res.data || res.data.success !== true) {
      message.error(res.data?.message || '提交失败');
      return;
    }

    message.success('景点创建成功！');
    handleReset();
  } catch (err) {
    console.error(err);
    message.error('提交失败，请检查服务器连接');
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.admin-container {
  min-height: 100vh;
  background-color: #f5f7fa;
  padding: 40px 20px;
}

.form-card {
  max-width: 650px;
  margin: 0 auto;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.05);
}

.sub-title {
  color: #999;
  font-size: 13px;
  font-weight: normal;
}

.upload-hint {
  color: #bfbfbf;
  font-size: 12px;
  margin-top: 8px;
}

.form-actions {
  margin-top: 24px;
  margin-bottom: 0;
}

/* 样式深度优化 */
:deep(.ant-card-head-title) {
  font-weight: bold;
  font-size: 18px;
}

:deep(.ant-input-affix-wrapper),
:deep(.ant-input),
:deep(.ant-btn) {
  border-radius: 6px;
}
</style>
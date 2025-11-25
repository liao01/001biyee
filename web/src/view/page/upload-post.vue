<template>
  <div style="padding: 30px">
    <a-card title="新的创作" class="post-editor" :bordered="false">
      <!-- 标题 -->
      <a-input v-model:value="title" style="height: 30px;font-size: 30px" :bordered="false" placeholder="输入标题" />
      <a-textarea v-model:value="content" style="font-size: 20px;margin-top: 20px" placeholder="Basic usage" :rows="4" />
      <div class="clearfix">
        <a-upload
            v-model:file-list="fileList"
            :customRequest="handleUpload"
            list-type="picture-card"
            @preview="handlePreview"
        >
          <div v-if="fileList.length < 8">
            <plus-outlined />
            <div style="margin-top: 8px">Upload</div>
          </div>
        </a-upload>

        <a-modal
            :open="previewVisible"
            :title="previewTitle"
            :footer="null"
            @cancel="handleCancel"
        >
          <img alt="preview" style="width: 100%" :src="previewImage" />
        </a-modal>
      </div>
      <span style="margin-right: 8px">标签:</span>
      <a-space :size="[0, 8]" wrap>
        <a-checkable-tag
            v-for="(tag, index) in tagsData"
            :key="tag"
            v-model:checked="selectTags[index]"
            @change="checked => handleChange(tag, checked)"
        >
          {{ tag }}
        </a-checkable-tag>
      </a-space>
      <br>
      <a-button type="primary" @click="submitPost" style="margin-top: 16px">
      发布
    </a-button>
    </a-card>
  </div>
</template>
<script setup>
import {ref, computed, reactive} from 'vue';
import { message } from 'ant-design-vue';
import axios from 'axios';
import store from "../../store/index.js";

const tagsData = reactive(['美食', '景点', '旅行', '攻略']);
const selectTags = reactive([false, true, false, false]);
const handleChange = (tag, checked) => {
  console.log(tag, checked);
};

// 标题、内容
const title = ref('');
const content = ref('');

// 标签
const tags = ref([]); // ["美食", "test"]

// 图片上传
const fileList = ref([]); // 文件列表
const previewVisible = ref(false);
const previewImage = ref('');
const previewTitle = ref('');

// base64 工具
function getBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsDataURL(file);
    reader.onload = () => resolve(reader.result);
    reader.onerror = error => reject(error);
  });
}

// 预览图片
const handleCancel = () => {
  previewVisible.value = false;
  previewTitle.value = '';
};

const handlePreview = async file => {
  if (!file.url && !file.preview) {
    file.preview = await getBase64(file.originFileObj);
  }
  previewImage.value = file.url || file.preview;
  previewVisible.value = true;
  previewTitle.value = file.name || '';
};

// 用户信息
const member = computed(() => store.state.member);

// 发布帖子
const submitPost = async () => {
  if (!title.value || !content.value) {
    message.warning("标题或内容不能为空！");
    return;
  }

  try {
    // 处理图片数据，转成 Base64
    const images = await Promise.all(fileList.value.map(async (file, index) => ({
      imageUrl: file.url || await getBase64(file.originFileObj), // 转 Base64
      seq: index + 1,
      description: file.description || ''
    })));

    // 处理标签
    const tagList = tagsData
        .filter((tag, index) => selectTags[index])
        .map(name => ({ name }));

    // 构建请求体
    const postData = {
      userId: member.value.id,
      title: title.value,
      content: content.value,
      locationId: 3, // 可以改成动态选择
      status: "1",
      images: images,
      tags: tagList
    };

    const res = await axios.post(
        "http://localhost:8080/lyw/web/post/post-save",
        postData,
        { headers: { "Content-Type": "application/json" } }
    );
    console.log("userId:", member.value?.id);
    if (res.data.success) {
      message.success("发布成功！");
      // 清空内容
      title.value = '';
      content.value = '';
      fileList.value = [];
      tags.value = [];
    } else {
      message.error(res.data.message || "发布失败！");
    }
  } catch (error) {
    console.error(error);
    message.error("请求失败，请检查后台接口或网络");
  }
};
const handleUpload = async ({ file, onSuccess, onError }) => {
  try {
    const base64 = await getBase64(file);
    file.url = base64; // 用 base64 模拟上传结果（或上传到后端）

    // 如果你有单独的上传接口：
    // const res = await axios.post("http://localhost:8080/lyw/web/upload", { image: base64 });

    onSuccess(); // 上传成功
  } catch (err) {
    onError(err);
  }
};
</script>

<style>
.post-editor {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
  width: 1800px;
  height: 500px;
}
.ant-upload-select-picture-card i {
  font-size: 32px;
  color: #999;
}

.ant-upload-select-picture-card .ant-upload-text {
  margin-top: 8px;
  color: #666;
}
.clearfix{
  margin-top: 20px;
}

</style>
<template>
  <div class="travel-page publish-page">
    <header class="travel-page__header">
      <div>
        <h1 class="travel-page__title">发布一段新的旅行</h1>
        <p class="travel-page__subtitle">用图片、文字和标签，把值得记住的风景分享出去。</p>
      </div>
    </header>

    <section class="travel-panel post-editor">
      <div class="publish-page__writing">
        <label>标题</label>
        <a-input v-model:value="title" class="publish-page__title-input" :bordered="false" placeholder="给这段旅程起个标题" />
        <label>旅行正文</label>
        <a-textarea v-model:value="content" class="publish-page__content-input" placeholder="写下路线、感受和实用建议…" :rows="8" />
      </div>

      <div class="publish-page__media">
        <div class="publish-page__section-heading">
          <strong>旅行图片</strong>
          <span>{{ fileList.length }}/8</span>
        </div>
        <a-upload
            v-model:file-list="fileList"
            :customRequest="handleUpload"
            list-type="picture-card"
            @preview="handlePreview"
        >
          <div v-if="fileList.length < 8">
            <plus-outlined />
            <div class="publish-page__upload-copy">添加图片</div>
          </div>
        </a-upload>

        <a-modal
            :open="previewVisible"
            :title="previewTitle"
            :footer="null"
            @cancel="handleCancel"
        >
          <img alt="preview" class="publish-page__preview" :src="previewImage">
        </a-modal>
      </div>
      <div class="publish-page__tags">
        <strong>内容标签</strong>
        <a-space :size="[8, 8]" wrap>
        <a-checkable-tag
            v-for="(tag, index) in tagsData"
            :key="tag"
            v-model:checked="selectTags[index]"
            @change="checked => handleChange(tag, checked)"
        >
          {{ tag }}
        </a-checkable-tag>
        </a-space>
      </div>
      <footer class="publish-page__footer">
        <span>发布前请确认图片和文字均为你有权分享的内容。</span>
        <a-button type="primary" @click="submitPost">发布旅行</a-button>
      </footer>
    </section>
  </div>
</template>
<script setup>
import {ref, computed, reactive} from 'vue';
import { message } from 'ant-design-vue';
import axios from 'axios';
import store from "../../store/index.js";
import { BASE_URL } from "../../utils/baseUrl";

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
        BASE_URL +"/lyw/web/post/post-save",
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

<style scoped>
.post-editor {
  max-width: 980px;
  overflow: hidden;
  padding: 34px;
}

.publish-page__writing {
  display: grid;
  gap: 12px;
}

.publish-page__writing label,
.publish-page__section-heading strong,
.publish-page__tags > strong {
  color: var(--travel-color-text);
  font-size: 14px;
  font-weight: 680;
}

.publish-page__title-input {
  border-bottom: 1px solid var(--travel-color-border) !important;
  border-radius: 0;
  font-size: clamp(24px, 3vw, 34px);
  font-weight: 720;
  height: 58px;
  padding-inline: 0;
}

.publish-page__content-input {
  border-color: var(--travel-color-border);
  border-radius: var(--travel-radius-md);
  font-size: 16px;
  line-height: 1.8;
  margin-bottom: 8px;
  padding: 16px;
}

.publish-page__media,
.publish-page__tags {
  border-top: 1px solid var(--travel-color-border);
  margin-top: 24px;
  padding-top: 24px;
}

.publish-page__section-heading {
  display: flex;
  justify-content: space-between;
  margin-bottom: 14px;
}

.publish-page__section-heading span,
.publish-page__footer span {
  color: var(--travel-color-text-muted);
  font-size: 12px;
}

.publish-page__upload-copy {
  color: var(--travel-color-text-secondary);
  margin-top: 8px;
}

.publish-page__preview {
  width: 100%;
}

.publish-page__tags {
  display: grid;
  gap: 14px;
}

.publish-page__footer {
  align-items: center;
  border-top: 1px solid var(--travel-color-border);
  display: flex;
  justify-content: space-between;
  margin: 28px -34px -34px;
  padding: 20px 34px;
}

:deep(.ant-btn-primary) {
  background: var(--travel-color-brand);
  border-color: var(--travel-color-brand);
  border-radius: 10px;
  height: 42px;
  padding-inline: 22px;
}

@media (max-width: 599px) {
  .post-editor { padding: 22px; }
  .publish-page__footer {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
    margin: 24px -22px -22px;
    padding: 18px 22px;
  }
}

</style>

<template>
  <div class="travel-page publish-page">
    <header class="travel-page__header publish-page__header">
      <div>
        <span class="publish-page__eyebrow">创作中心</span>
        <h1 class="travel-page__title">发布一段新的旅行</h1>
        <p class="travel-page__subtitle">用真实的图片与文字，记录值得再次抵达的风景。</p>
      </div>
      <span class="publish-page__draft-state">内容将直接公开发布</span>
    </header>

    <section class="publish-editor">
      <main class="travel-panel publish-editor__main">
        <div class="publish-page__writing">
          <label for="post-title">标题</label>
          <a-input id="post-title" v-model:value="title" class="publish-page__title-input" :bordered="false" :maxlength="80" placeholder="给这段旅程起个标题" />

          <div class="publish-page__field-heading">
            <label for="post-content">旅行正文</label>
            <span>{{ content.length }} 字</span>
          </div>
          <a-textarea id="post-content" v-model:value="content" class="publish-page__content-input" placeholder="写下路线、感受和实用建议……" :rows="12" />
        </div>

        <div class="publish-page__media">
          <div class="publish-page__section-heading">
            <div>
              <strong>旅行图片</strong>
              <p>第一张图片将作为内容封面，建议使用横向高清图片。</p>
            </div>
            <span>{{ fileList.length }}/8</span>
          </div>
          <a-upload v-model:file-list="fileList" :custom-request="handleUpload" list-type="picture-card" accept="image/*" @preview="handlePreview">
            <div v-if="fileList.length < 8">
              <PlusOutlined />
              <div class="publish-page__upload-copy">添加图片</div>
            </div>
          </a-upload>
        </div>
      </main>

      <aside class="travel-panel publish-editor__settings" aria-label="发布设置">
        <div>
          <span class="publish-editor__step">发布设置</span>
          <h2>选择内容分类</h2>
          <p>分类会显示在内容卡片上，也会决定内容出现在哪个发现频道。</p>
        </div>

        <div v-if="categoriesLoading" class="publish-editor__category-status">正在读取分类…</div>
        <fieldset v-else class="publish-editor__categories">
          <legend class="sr-only">内容分类</legend>
          <label v-for="category in categoryOptions" :key="category.code" :class="['publish-editor__category', { 'is-selected': categoryCode === category.code }]">
            <input v-model="categoryCode" type="radio" name="post-category" :value="category.code">
            <span>
              <strong>{{ category.name }}</strong>
              <small>{{ categoryDescription(category.code) }}</small>
            </span>
          </label>
        </fieldset>

        <div class="publish-editor__notice">
          <SafetyCertificateOutlined />
          <span>发布前请确认图片和文字均为你有权分享的内容。</span>
        </div>

        <button class="travel-primary-button publish-page__submit" type="button" :disabled="submitting || categoriesLoading" @click="submitPost">
          {{ submitting ? '正在发布…' : '发布旅行' }}
        </button>
      </aside>
    </section>

    <a-modal :open="previewVisible" :title="previewTitle" :footer="null" @cancel="handleCancel">
      <img alt="图片预览" class="publish-page__preview" :src="previewImage">
    </a-modal>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { PlusOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import axios from 'axios'

import { fetchPostCategories } from '../../api/postCategories.js'
import { BASE_URL } from '../../utils/baseUrl.js'
import { buildPostPayload, validatePostDraft } from './publishPostForm.js'

const baseUrl = `${BASE_URL}/lyw`
const title = ref('')
const content = ref('')
const categoryCode = ref('')
const categoryOptions = ref([])
const categoriesLoading = ref(true)
const submitting = ref(false)
const fileList = ref([])
const previewVisible = ref(false)
const previewImage = ref('')
const previewTitle = ref('')

const categoryDescriptions = {
  CITY_WALK: '城市街区、人文路线与周末漫游',
  NATURAL_SCENERY: '山川、海岸、森林与户外风景',
  FOOD: '地方味道、餐馆体验与美食路线',
}
const categoryDescription = code => categoryDescriptions[code] || '旅行内容分类'

const getBase64 = file => new Promise((resolve, reject) => {
  const reader = new FileReader()
  reader.readAsDataURL(file)
  reader.onload = () => resolve(reader.result)
  reader.onerror = reject
})

const handleCancel = () => {
  previewVisible.value = false
  previewTitle.value = ''
}

const handlePreview = async file => {
  if (!file.url && !file.preview) file.preview = await getBase64(file.originFileObj)
  previewImage.value = file.url || file.preview
  previewVisible.value = true
  previewTitle.value = file.name || ''
}

const handleUpload = async ({ file, onSuccess, onError }) => {
  try {
    file.url = await getBase64(file)
    onSuccess()
  } catch (error) {
    onError(error)
  }
}

const imagePayload = async () => Promise.all(fileList.value.map(async (file, index) => ({
  imageUrl: file.url || await getBase64(file.originFileObj),
  seq: index + 1,
  description: file.description || '',
})))

const submitPost = async () => {
  const validationMessage = validatePostDraft({ title: title.value, content: content.value, categoryCode: categoryCode.value, images: fileList.value })
  if (validationMessage) {
    message.warning(validationMessage)
    return
  }

  submitting.value = true
  try {
    const postData = buildPostPayload({ title: title.value, content: content.value, categoryCode: categoryCode.value, images: await imagePayload() })
    const { data } = await axios.post(`${baseUrl}/web/post/post-save`, postData, { headers: { 'Content-Type': 'application/json' } })
    if (!data.success) {
      message.error(data.message || '发布失败')
      return
    }
    message.success('发布成功')
    title.value = ''
    content.value = ''
    categoryCode.value = ''
    fileList.value = []
  } catch (error) {
    message.error('请求失败，请检查后台接口或网络')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    categoryOptions.value = await fetchPostCategories(axios, baseUrl)
  } catch (error) {
    message.error(error.message || '分类加载失败')
  } finally {
    categoriesLoading.value = false
  }
})
</script>

<style scoped>
.publish-page { max-width: 1280px; }
.publish-page__header { align-items: end; display: flex; justify-content: space-between; }
.publish-page__eyebrow,
.publish-editor__step { color: var(--travel-color-brand); font-size: 12px; font-weight: 720; letter-spacing: .1em; text-transform: uppercase; }
.publish-page__draft-state { color: var(--travel-color-text-muted); font-size: 13px; }
.publish-editor { align-items: start; display: grid; gap: 24px; grid-template-columns: minmax(0, 1fr) 330px; }
.publish-editor__main { padding: 36px; }
.publish-page__writing { display: grid; gap: 13px; }
.publish-page__writing label,
.publish-page__section-heading strong { color: var(--travel-color-text); font-size: 14px; font-weight: 680; }
.publish-page__field-heading,
.publish-page__section-heading { align-items: start; display: flex; justify-content: space-between; }
.publish-page__field-heading span,
.publish-page__section-heading > span { color: var(--travel-color-text-muted); font-size: 12px; }
.publish-page__title-input { border-bottom: 1px solid var(--travel-color-border) !important; border-radius: 0; font-size: clamp(24px, 3vw, 34px); font-weight: 720; height: 60px; padding-inline: 0; }
.publish-page__content-input { border-color: var(--travel-color-border); border-radius: var(--travel-radius-md); font-size: 16px; line-height: 1.8; padding: 18px; resize: vertical; }
.publish-page__media { border-top: 1px solid var(--travel-color-border); margin-top: 28px; padding-top: 26px; }
.publish-page__section-heading { margin-bottom: 16px; }
.publish-page__section-heading p { color: var(--travel-color-text-muted); font-size: 12px; margin: 5px 0 0; }
.publish-page__upload-copy { color: var(--travel-color-text-secondary); margin-top: 8px; }
.publish-page__preview { width: 100%; }
.publish-editor__settings { display: grid; gap: 14px; padding: 20px; position: sticky; top: 24px; }
.publish-editor__settings h2 { font-size: 22px; margin: 8px 0 6px; }
.publish-editor__settings p { color: var(--travel-color-text-secondary); font-size: 13px; line-height: 1.65; margin: 0; }
.publish-editor__categories { border: 0; display: grid; gap: 8px; margin: 0; padding: 0; }
.publish-editor__category { align-items: center; border: 1px solid var(--travel-color-border); border-radius: 12px; cursor: pointer; display: flex; padding: 10px 12px; transition: border-color var(--travel-transition), background var(--travel-transition); }
.publish-editor__category.is-selected { background: rgb(255 59 79 / 5%); border-color: var(--travel-color-brand); }
.publish-editor__category input { accent-color: var(--travel-color-brand); margin: 0 12px 0 0; }
.publish-editor__category span { display: grid; gap: 3px; }
.publish-editor__category strong { font-size: 14px; }
.publish-editor__category small { color: var(--travel-color-text-muted); line-height: 1.4; }
.publish-editor__category-status { color: var(--travel-color-text-muted); font-size: 13px; padding-block: 14px; }
.publish-editor__notice { align-items: flex-start; background: var(--travel-color-bg-subtle); border-radius: 10px; color: var(--travel-color-text-secondary); display: flex; font-size: 12px; gap: 9px; line-height: 1.55; padding: 12px; }
.publish-page__submit { border: 0; cursor: pointer; min-height: 46px; width: 100%; }
.publish-page__submit:disabled { cursor: not-allowed; opacity: .55; }
.sr-only { height: 1px; margin: -1px; overflow: hidden; padding: 0; position: absolute; width: 1px; clip: rect(0, 0, 0, 0); }
@media (max-width: 899px) {
  .publish-editor { grid-template-columns: 1fr; }
  .publish-editor__settings { position: static; }
}
@media (max-width: 599px) {
  .publish-page__header { align-items: flex-start; flex-direction: column; gap: 10px; }
  .publish-editor__main,
  .publish-editor__settings { padding: 22px; }
}
</style>

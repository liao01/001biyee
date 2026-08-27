<template>
  <article class="post-card">
    <button class="post-card__action custom-card" type="button" @click="emit('open', post)">
      <div class="post-card__media">
        <img :src="post.image" :alt="post.title" loading="lazy">
        <span v-if="post.location" class="post-card__location">
          <EnvironmentOutlined />
          {{ post.location }}
        </span>
      </div>

      <div class="post-card__body">
        <h2>{{ post.title }}</h2>
        <p v-if="post.description" class="post-card__description">{{ post.description }}</p>
        <div class="post-card__meta">
          <span class="post-card__author">
            <UserOutlined />
            {{ post.author || '旅分享用户' }}
          </span>
          <time v-if="post.publishedAt">{{ formatDate(post.publishedAt) }}</time>
        </div>
      </div>
    </button>
  </article>
</template>

<script setup>
import { EnvironmentOutlined, UserOutlined } from '@ant-design/icons-vue'

defineProps({
  post: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['open'])

const formatDate = (value) => {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
  })
}
</script>

<style scoped>
.post-card {
  break-inside: avoid;
  margin-bottom: 24px;
}

.post-card__action {
  background: #fff;
  border: 1px solid var(--travel-color-border);
  border-radius: var(--travel-radius-lg);
  color: inherit;
  cursor: pointer;
  display: block;
  overflow: hidden;
  padding: 0;
  text-align: left;
  transition: border-color var(--travel-transition), transform var(--travel-transition), box-shadow var(--travel-transition);
  width: 100%;
}

.post-card__action:hover {
  border-color: #d8dbe1;
  box-shadow: 0 14px 34px rgb(24 30 40 / 9%);
  transform: translateY(-3px);
}

.post-card__media {
  aspect-ratio: 4 / 3;
  background: var(--travel-color-bg-subtle);
  overflow: hidden;
  position: relative;
}

.post-card:nth-child(3n + 1) .post-card__media {
  aspect-ratio: 4 / 5;
}

.post-card__media img {
  height: 100%;
  object-fit: cover;
  transition: transform 420ms ease;
  width: 100%;
}

.post-card__action:hover img {
  transform: scale(1.025);
}

.post-card__location {
  align-items: center;
  background: rgb(255 255 255 / 92%);
  border-radius: 8px;
  bottom: 12px;
  color: var(--travel-color-text);
  display: inline-flex;
  font-size: 12px;
  font-weight: 600;
  gap: 5px;
  left: 12px;
  max-width: calc(100% - 24px);
  padding: 6px 9px;
  position: absolute;
}

.post-card__body {
  padding: 16px;
}

.post-card h2 {
  color: var(--travel-color-text);
  font-size: 17px;
  font-weight: 700;
  letter-spacing: -.02em;
  line-height: 1.45;
  margin: 0;
}

.post-card__description {
  color: var(--travel-color-text-secondary);
  display: -webkit-box;
  font-size: 13px;
  line-height: 1.65;
  margin: 8px 0 0;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.post-card__meta {
  align-items: center;
  color: var(--travel-color-text-muted);
  display: flex;
  font-size: 12px;
  gap: 12px;
  justify-content: space-between;
  margin-top: 14px;
}

.post-card__author {
  align-items: center;
  display: inline-flex;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

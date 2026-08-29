<template>
  <section class="profile-header">
    <div class="profile-header__identity">
      <div class="profile-header__avatar">
        <img v-if="profile.avatar" :src="profile.avatar" :alt="`${profile.name}的头像`">
        <UserOutlined v-else />
      </div>
      <div class="profile-header__copy">
        <h1>{{ profile.name || '旅行者' }}</h1>
        <p>{{ profile.bio || '把走过的路，写成可以再次抵达的故事。' }}</p>
        <span v-if="profile.location" class="profile-header__location">
          <EnvironmentOutlined />
          {{ profile.location }}
        </span>
      </div>
    </div>

    <dl v-if="profile.stats?.length" class="profile-header__stats">
      <div v-for="stat in profile.stats" :key="stat.label">
        <dt>{{ stat.label }}</dt>
        <dd>{{ formatNumber(stat.value) }}</dd>
      </div>
    </dl>

    <div v-if="showActions" class="profile-header__actions">
      <template v-if="isSelf">
        <button class="travel-secondary-button" type="button" aria-label="编辑个人资料" @click="emit('edit')">编辑资料</button>
      </template>
      <button
        v-else
        :class="following ? 'travel-secondary-button' : 'travel-primary-button'"
        type="button"
        aria-label="关注旅行者"
        :aria-pressed="following"
        @click="emit('follow')"
      >{{ following ? '已关注' : '关注' }}</button>
    </div>
  </section>
</template>

<script setup>
import { EnvironmentOutlined, UserOutlined } from '@ant-design/icons-vue'

defineProps({
  profile: {
    type: Object,
    required: true,
  },
  isSelf: {
    type: Boolean,
    default: false,
  },
  following: {
    type: Boolean,
    default: false,
  },
  showActions: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['edit', 'follow'])

const formatNumber = (value) => new Intl.NumberFormat('zh-CN').format(Number(value) || 0)
</script>

<style scoped>
.profile-header {
  align-items: center;
  background: #fff;
  border: 1px solid var(--travel-color-border);
  border-radius: var(--travel-radius-lg);
  display: grid;
  gap: 28px;
  grid-template-columns: minmax(280px, 1fr) auto auto;
  min-height: 210px;
  overflow: hidden;
  padding: 34px 38px;
  position: relative;
}

.profile-header::after {
  border: 1px dashed rgb(255 59 79 / 28%);
  border-radius: 50%;
  content: '';
  height: 190px;
  pointer-events: none;
  position: absolute;
  right: 10%;
  top: -92px;
  width: 360px;
}

.profile-header__identity {
  align-items: center;
  display: flex;
  gap: 22px;
  min-width: 0;
  position: relative;
  z-index: 1;
}

.profile-header__avatar {
  align-items: center;
  background: var(--travel-color-bg-subtle);
  border: 1px solid var(--travel-color-border);
  border-radius: 50%;
  color: var(--travel-color-text-muted);
  display: flex;
  flex: 0 0 auto;
  font-size: 32px;
  height: 108px;
  justify-content: center;
  overflow: hidden;
  width: 108px;
}

.profile-header__avatar img {
  height: 100%;
  object-fit: cover;
  width: 100%;
}

.profile-header h1 {
  font-size: clamp(26px, 3vw, 36px);
  letter-spacing: -.04em;
  margin: 0;
}

.profile-header__copy p {
  color: var(--travel-color-text-secondary);
  line-height: 1.65;
  margin: 8px 0;
}

.profile-header__location {
  align-items: center;
  color: var(--travel-color-text-muted);
  display: inline-flex;
  font-size: 13px;
  gap: 6px;
}

.profile-header__stats {
  display: flex;
  gap: 26px;
  margin: 0;
  position: relative;
  z-index: 1;
}

.profile-header__stats div {
  display: flex;
  flex-direction: column-reverse;
  gap: 4px;
}

.profile-header__stats dt {
  color: var(--travel-color-text-muted);
  font-size: 12px;
}

.profile-header__stats dd {
  color: var(--travel-color-text);
  font-size: 20px;
  font-weight: 720;
  margin: 0;
}

.profile-header__actions {
  display: flex;
  gap: 10px;
  position: relative;
  z-index: 1;
}

@media (max-width: 1099px) {
  .profile-header {
    grid-template-columns: 1fr auto;
  }

  .profile-header__stats {
    grid-column: 1 / -1;
  }
}

@media (max-width: 699px) {
  .profile-header {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    padding: 24px;
  }

  .profile-header__avatar {
    height: 82px;
    width: 82px;
  }

  .profile-header__stats {
    flex-wrap: wrap;
  }
}
</style>

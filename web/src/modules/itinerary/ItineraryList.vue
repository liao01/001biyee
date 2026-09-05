<template>
  <main class="travel-page itinerary-page">
    <header class="travel-page__header itinerary-hero">
      <div>
        <p class="travel-page__eyebrow">TRIP PLANNER</p>
        <h1 class="travel-page__title">我的行程</h1>
        <p class="travel-page__subtitle">把目的地、每天的安排和旅行状态放在同一处。</p>
      </div>
      <RouterLink class="travel-primary-button" to="/itineraries/new">创建行程</RouterLink>
    </header>

    <section class="itinerary-toolbar" aria-label="行程筛选">
      <button
        v-for="filter in filters"
        :key="filter.value"
        type="button"
        :class="['itinerary-filter', { 'is-active': selectedStatus === filter.value }]"
        :aria-pressed="selectedStatus === filter.value"
        @click="selectFilter(filter.value)"
      >{{ filter.label }}</button>
    </section>

    <div v-if="loading" class="itinerary-state" role="status">正在加载行程…</div>
    <div v-else-if="errorMessage" class="itinerary-state" role="alert">
      <p>{{ errorMessage }}</p>
      <button class="travel-primary-button" type="button" aria-label="重试加载行程" @click="load">重试</button>
    </div>
    <div v-else-if="!items.length" class="travel-panel travel-empty">
      <h2>还没有行程</h2>
      <p>从一个目的地和日期开始，稍后再逐天完善。</p>
      <RouterLink class="travel-primary-button" to="/itineraries/new">创建第一个行程</RouterLink>
    </div>
    <section v-else class="itinerary-grid" aria-label="行程列表">
      <RouterLink
        v-for="item in items"
        :key="item.id"
        class="itinerary-card travel-panel"
        :to="`/itineraries/${item.id}`"
      >
        <div class="itinerary-card__topline">
          <span :class="['itinerary-status', `itinerary-status--${item.status.toLowerCase()}`]">
            {{ statusLabel(item.status) }}
          </span>
          <time :datetime="item.updatedAt">更新于 {{ formatUpdatedAt(item.updatedAt) }}</time>
        </div>
        <h2>{{ item.title }}</h2>
        <p class="itinerary-card__destination">{{ item.primaryDestination }}</p>
        <p class="itinerary-card__dates">{{ formatDateRange(item.startDate, item.endDate) }}</p>
        <span class="itinerary-card__link">打开行程 <span aria-hidden="true">→</span></span>
      </RouterLink>
    </section>
  </main>
</template>

<script setup>
import { inject, onMounted, ref } from 'vue'
import { formatDateRange } from './itineraryFormatters.js'
import { itineraryHttp, itineraryHttpKey } from './itineraryHttp.js'
import './itinerary.css'

const api = inject(itineraryHttpKey, itineraryHttp)
const items = ref([])
const loading = ref(true)
const errorMessage = ref('')
const selectedStatus = ref('')
const filters = [
  { value: '', label: '当前行程' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PLANNED,IN_PROGRESS', label: '即将出发 / 进行中' },
  { value: 'ARCHIVED', label: '已归档' },
]
const labels = {
  DRAFT: '草稿', PLANNED: '已计划', IN_PROGRESS: '进行中',
  COMPLETED: '已完成', CANCELLED: '已取消', ARCHIVED: '已归档',
}

const statusLabel = (status) => labels[status] || status
const formatUpdatedAt = (value) => {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '未知时间'
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(date)
}
const load = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const statuses = selectedStatus.value ? selectedStatus.value.split(',') : undefined
    items.value = (await api.list({ status: statuses, limit: 50 })).items
  } catch (error) {
    errorMessage.value = error.message || '暂时无法读取行程，请重试。'
  } finally {
    loading.value = false
  }
}
const selectFilter = (status) => {
  selectedStatus.value = status
  void load()
}
onMounted(load)
</script>

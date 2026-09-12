<template>
  <section class="route-leg-list" aria-label="每日路线文字说明">
    <ol class="route-leg-list__stops">
      <li v-for="stop in stops" :key="stop.itemId">
        <strong>{{ stop.sequence }}. {{ stop.title }}</strong>
        <span v-if="stop.status !== 'RESOLVED'"> · 暂未确认地图地点</span>
        <small v-else-if="stop.placeName"> · {{ stop.placeName }}</small>
      </li>
    </ol>
    <ul v-if="legs.length" class="route-leg-list__legs" aria-label="路线段说明">
      <li v-for="leg in legs" :key="`${leg.fromItemId}-${leg.toItemId}`" :class="`route-leg-list__leg--${leg.status.toLowerCase()}`">
        <strong>{{ stopTitle(leg.fromItemId) }} → {{ stopTitle(leg.toItemId) }}</strong>
        <span v-if="leg.status === 'UNAVAILABLE'">暂时无法获取路线</span>
        <span v-else>{{ formatDistance(leg.distanceMeters) }} · {{ formatDuration(leg.durationSeconds) }} · {{ routeStatus(leg) }}</span>
      </li>
    </ul>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  stops: { type: Array, default: () => [] },
  legs: { type: Array, default: () => [] },
})

const stopById = computed(() => new Map(props.stops.map((stop) => [String(stop.itemId), stop])))

const stopTitle = (itemId) => stopById.value.get(String(itemId))?.title || `安排 ${itemId}`
const formatDistance = (meters) => {
  const value = Number(meters)
  if (!Number.isFinite(value)) return '距离未知'
  return value < 1000 ? `${Math.round(value)} 米` : `${(value / 1000).toFixed(1)} 公里`
}
const formatDuration = (seconds) => {
  const minutes = Math.round(Number(seconds) / 60)
  if (!Number.isFinite(minutes)) return '时长未知'
  if (minutes < 60) return `${minutes} 分钟`
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`
}
const routeStatus = (leg) => {
  if (leg.status === 'LIVE') return '实时路线'
  return leg.stale ? '缓存路线（可能不是最新）' : '缓存路线'
}
</script>

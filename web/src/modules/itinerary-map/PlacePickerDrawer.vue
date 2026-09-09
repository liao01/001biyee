<template>
  <aside
    v-if="open"
    class="place-picker"
    role="dialog"
    aria-labelledby="place-picker-title"
    @keydown.esc.prevent="close"
  >
    <header class="place-picker__header">
      <div>
        <p class="travel-page__eyebrow">PLACE REFERENCE</p>
        <h2 id="place-picker-title">为“{{ item?.title }}”设置地点</h2>
      </div>
      <button type="button" aria-label="关闭地点选择" @click="close">关闭</button>
    </header>

    <p class="place-picker__hint">地点名称会保留；这里补充可用于地图和路线的经纬度。</p>
    <div class="place-picker__search">
      <label for="place-picker-keyword">搜索地点</label>
      <div>
        <input
          id="place-picker-keyword"
          ref="searchInput"
          v-model.trim="keyword"
          aria-label="搜索地点"
          type="search"
          maxlength="100"
          autocomplete="off"
          @keydown.enter.prevent="search"
        >
        <button type="button" aria-label="搜索地点" :disabled="searching || saving" @click="search">
          {{ searching ? '搜索中…' : '搜索' }}
        </button>
      </div>
    </div>

    <p class="place-picker__live" role="status" aria-live="polite" aria-atomic="true">{{ liveMessage }}</p>
    <p v-if="serviceError" class="place-picker__error" role="alert">{{ serviceError }}；你仍可手工录入地点。</p>

    <ul v-if="candidates.length" class="place-picker__candidates" aria-label="地点搜索结果">
      <li v-for="candidate in candidates" :key="`${candidate.source}:${candidate.sourcePlaceId || candidate.name}`">
        <button
          type="button"
          :aria-label="`选择地点 ${candidate.name}`"
          :aria-pressed="selectedCandidate === candidate"
          :class="{ 'is-selected': selectedCandidate === candidate }"
          @click="selectCandidate(candidate)"
        >
          <strong>{{ candidate.name }}</strong>
          <span>{{ candidate.address || '未提供地址' }}</span>
        </button>
      </li>
    </ul>

    <section v-if="selectedCandidate" class="place-picker__preview" aria-label="已选地点预览">
      <strong>已选：{{ selectedCandidate.name }}</strong>
      <span>{{ selectedCandidate.address || '未提供地址' }}</span>
      <small>{{ selectedCandidate.longitude }}, {{ selectedCandidate.latitude }} · {{ selectedCandidate.coordinateSystem }}</small>
    </section>

    <section class="place-picker__manual" aria-labelledby="place-picker-manual-title">
      <h3 id="place-picker-manual-title">手工录入</h3>
      <p>搜索不可用或结果不准确时，可录入 GCJ-02 坐标。</p>
      <div class="itinerary-form__grid">
        <div class="itinerary-field itinerary-field--wide">
          <label for="place-picker-manual-name">地点名称</label>
          <input id="place-picker-manual-name" v-model.trim="manual.name" aria-label="手工地点名称" type="text" maxlength="200">
        </div>
        <div class="itinerary-field">
          <label for="place-picker-longitude">经度</label>
          <input id="place-picker-longitude" v-model.trim="manual.longitude" aria-label="经度" inputmode="decimal" type="text" maxlength="16">
        </div>
        <div class="itinerary-field">
          <label for="place-picker-latitude">纬度</label>
          <input id="place-picker-latitude" v-model.trim="manual.latitude" aria-label="纬度" inputmode="decimal" type="text" maxlength="16">
        </div>
        <div class="itinerary-field itinerary-field--wide">
          <label for="place-picker-manual-address">地址（可选）</label>
          <input id="place-picker-manual-address" v-model.trim="manual.address" type="text" maxlength="500">
        </div>
      </div>
      <p v-if="manualError" class="place-picker__error" role="alert">{{ manualError }}</p>
    </section>

    <footer class="place-picker__actions">
      <button class="travel-secondary-button" type="button" :disabled="saving" @click="useManual">使用手工地点</button>
      <button class="travel-primary-button" type="button" aria-label="确认地点" :disabled="saving" @click="confirm">
        {{ saving ? '正在保存…' : '确认地点' }}
      </button>
    </footer>
  </aside>
</template>

<script setup>
import { nextTick, reactive, ref, watch } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  item: { type: Object, default: null },
  city: { type: String, default: '' },
  mapApi: { type: Object, required: true },
  saving: { type: Boolean, default: false },
})
const emit = defineEmits(['close', 'confirm'])

const searchInput = ref(null)
const keyword = ref('')
const candidates = ref([])
const selectedCandidate = ref(null)
const searching = ref(false)
const serviceError = ref('')
const manualError = ref('')
const liveMessage = ref('')
const previousFocus = ref(null)
const manual = reactive({ name: '', longitude: '', latitude: '', address: '' })

const reset = () => {
  keyword.value = ''
  candidates.value = []
  selectedCandidate.value = null
  serviceError.value = ''
  manualError.value = ''
  liveMessage.value = ''
  Object.assign(manual, {
    name: props.item?.placeReference?.name || props.item?.placeName || '',
    longitude: props.item?.placeReference?.longitude == null ? '' : String(props.item.placeReference.longitude),
    latitude: props.item?.placeReference?.latitude == null ? '' : String(props.item.placeReference.latitude),
    address: props.item?.placeReference?.address || '',
  })
}

watch(() => props.open, async (opened, wasOpened) => {
  if (opened) {
    previousFocus.value = document.activeElement instanceof HTMLElement ? document.activeElement : null
    reset()
    await nextTick()
    searchInput.value?.focus()
  } else if (wasOpened) {
    await nextTick()
    previousFocus.value?.focus()
  }
}, { immediate: true, flush: 'post' })

const search = async () => {
  const query = keyword.value.trim()
  if (!query || searching.value || props.saving) return
  searching.value = true
  serviceError.value = ''
  manualError.value = ''
  selectedCandidate.value = null
  try {
    const result = await props.mapApi.searchPlaces({ keyword: query, city: props.city || undefined, limit: 10 })
    candidates.value = result.items || []
    liveMessage.value = candidates.value.length ? `找到 ${candidates.value.length} 个地点，请选择一个地点预览。` : '没有找到匹配地点，可以手工录入。'
  } catch (error) {
    candidates.value = []
    serviceError.value = error.message || '暂时无法搜索地点'
    liveMessage.value = ''
  } finally {
    searching.value = false
  }
}

const selectCandidate = (candidate) => {
  selectedCandidate.value = candidate
  manualError.value = ''
  liveMessage.value = `已选择 ${candidate.name}，确认后才会保存。`
}

const manualPlace = () => {
  const name = manual.name.trim()
  const longitudeText = manual.longitude.trim()
  const latitudeText = manual.latitude.trim()
  const longitude = Number(longitudeText)
  const latitude = Number(latitudeText)
  if (!name || !longitudeText || !latitudeText || !Number.isFinite(longitude) || !Number.isFinite(latitude)
    || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
    manualError.value = '请填写地点名称，以及有效的经度和纬度。'
    return null
  }
  return {
    source: 'MANUAL', sourcePlaceId: null, name, address: manual.address.trim() || null,
    administrativeAreaCode: null, longitude, latitude, coordinateSystem: 'GCJ02',
  }
}

const useManual = () => {
  selectedCandidate.value = null
  const place = manualPlace()
  if (place) liveMessage.value = `已准备手工地点 ${place.name}，确认后才会保存。`
}

const placePayload = (place) => ({
  source: place.source,
  sourcePlaceId: place.sourcePlaceId || null,
  name: place.name,
  address: place.address || null,
  administrativeAreaCode: place.administrativeAreaCode || null,
  longitude: Number(place.longitude),
  latitude: Number(place.latitude),
  coordinateSystem: place.coordinateSystem,
})

const confirm = () => {
  if (props.saving) return
  const place = selectedCandidate.value || manualPlace()
  if (place) emit('confirm', placePayload(place))
}

const close = () => emit('close')
</script>

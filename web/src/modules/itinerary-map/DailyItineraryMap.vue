<template>
  <section v-show="open" class="daily-itinerary-map" aria-label="每日行程地图">
    <header class="daily-itinerary-map__header">
      <div>
        <p class="travel-page__eyebrow">DAILY ROUTE</p>
        <h3>每日地图与路线</h3>
      </div>
      <div class="daily-itinerary-map__modes" aria-label="出行方式">
        <button v-for="option in modes" :key="option.value" type="button" :aria-label="`切换为${option.label}`" :aria-pressed="mode === option.value" :disabled="loading" @click="mode = option.value">{{ option.label }}</button>
      </div>
    </header>

    <p v-if="loading" class="daily-itinerary-map__status" role="status">正在计算路线…</p>
    <p v-else-if="requestError" class="daily-itinerary-map__error" role="alert">{{ requestError }}</p>
    <template v-else-if="dailyMap">
      <p v-if="dailyMap.degraded" class="daily-itinerary-map__degraded" role="status">部分路线暂不可用，以下保留了可用路线和所有安排。</p>
      <p v-if="mapError" class="daily-itinerary-map__error" role="alert">地图图层暂不可用，仍可查看文字路线。</p>
      <RouteLegList :stops="dailyMap.stops" :legs="dailyMap.legs" />
    </template>
    <div v-show="dailyMap && !mapError" ref="mapElement" class="daily-itinerary-map__canvas" aria-label="地图图层"></div>
  </section>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { loadAmap } from './amapLoader.js'
import RouteLegList from './RouteLegList.vue'
import './itineraryMap.css'

const props = defineProps({
  open: { type: Boolean, default: false },
  itineraryId: { type: String, required: true },
  dayId: { type: String, required: true },
  mapApi: { type: Object, required: true },
  mapLoader: { type: Function, default: loadAmap },
})

const modes = [
  { value: 'WALKING', label: '步行' },
  { value: 'DRIVING', label: '驾车' },
  { value: 'TRANSIT', label: '公交' },
]
const mode = ref('WALKING')
const loading = ref(false)
const requestError = ref('')
const mapError = ref('')
const dailyMap = ref(null)
const mapElement = ref(null)
let map = null
let markers = []
let polylines = []
let requestSequence = 0
let disposed = false

const clearOverlays = () => {
  for (const overlay of [...markers, ...polylines]) overlay.setMap?.(null)
  markers = []
  polylines = []
}

const destroyMap = () => {
  clearOverlays()
  map?.destroy?.()
  map = null
}

const resolvedStops = (value) => value.stops.filter((stop) => stop.status === 'RESOLVED' && stop.placeReference)

const drawMap = async (value, sequence) => {
  try {
    const AMap = await props.mapLoader()
    if (disposed || sequence !== requestSequence || !props.open) return
    await nextTick()
    if (!mapElement.value) return
    if (!map) {
      map = new AMap.Map(mapElement.value, { viewMode: '2D' })
    }
    clearOverlays()
    const stops = resolvedStops(value)
    markers = stops.map((stop) => {
      const marker = new AMap.Marker({
        position: [Number(stop.placeReference.longitude), Number(stop.placeReference.latitude)],
        title: `${stop.sequence}. ${stop.title}`,
      })
      marker.setMap?.(map)
      return marker
    })
    polylines = value.legs
      .filter((leg) => leg.status !== 'UNAVAILABLE' && Array.isArray(leg.polyline) && leg.polyline.length > 1)
      .map((leg) => {
        const polyline = new AMap.Polyline({ path: leg.polyline.map((point) => [Number(point.longitude), Number(point.latitude)]) })
        polyline.setMap?.(map)
        return polyline
      })
    if (markers.length === 1) {
      const reference = stops[0].placeReference
      map.setZoomAndCenter?.(14, [Number(reference.longitude), Number(reference.latitude)])
    } else if (markers.length > 1) {
      map.setFitView?.(markers)
    }
    mapError.value = ''
  } catch (error) {
    if (sequence === requestSequence) mapError.value = error.message || '地图图层加载失败'
  }
}

const refresh = async () => {
  if (disposed || !props.open) return
  const sequence = ++requestSequence
  loading.value = true
  requestError.value = ''
  mapError.value = ''
  try {
    const result = await props.mapApi.dailyMap(String(props.itineraryId), String(props.dayId), mode.value)
    if (sequence !== requestSequence) return
    dailyMap.value = result
    await nextTick()
    void drawMap(result, sequence)
  } catch (error) {
    if (sequence === requestSequence) {
      dailyMap.value = null
      requestError.value = error.message || '暂时无法读取每日路线'
    }
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

watch([() => props.open, mode], refresh, { immediate: true })
onBeforeUnmount(() => {
  disposed = true
  requestSequence += 1
  destroyMap()
})
</script>

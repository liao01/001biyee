<template>
  <main class="travel-page itinerary-page itinerary-editor-page">
    <div v-if="loading" class="itinerary-state" role="status">正在打开行程…</div>
    <div v-else-if="loadError" class="itinerary-state" role="alert">
      <p>{{ loadError }}</p>
      <button class="travel-primary-button" type="button" aria-label="重试打开行程" @click="load">重试</button>
    </div>
    <template v-else-if="editor">
      <header class="itinerary-editor-header">
        <div>
          <RouterLink class="itinerary-back-link" to="/itineraries">← 返回我的行程</RouterLink>
          <h1>{{ editor.state.snapshot.title }}</h1>
          <p>{{ formatDateRange(editor.state.snapshot.startDate, editor.state.snapshot.endDate) }} · {{ editor.state.snapshot.timeZone }}</p>
        </div>
        <div class="itinerary-editor-header__status">
          <span :class="['itinerary-status', `itinerary-status--${editor.state.snapshot.status.toLowerCase()}`]">{{ statusLabel(editor.state.snapshot.status) }}</span>
          <span role="status" aria-live="polite" aria-atomic="true">{{ saveLabel }}</span>
        </div>
      </header>

      <div v-if="operationError" class="itinerary-operation-error" role="alert">
        <div>
          <strong>{{ operationErrorTitle }}</strong>
          <p>{{ operationError.message }}</p>
        </div>
        <button v-if="editor.state.status === 'conflict' || projectionError" class="travel-secondary-button" type="button" aria-label="重新加载行程" @click="reload">重新加载</button>
        <button v-else class="travel-secondary-button" type="button" aria-label="重试保存" @click="retry">重试</button>
      </div>

      <section class="itinerary-editor-layout">
        <aside class="itinerary-editor-sidebar">
          <form class="travel-panel itinerary-compact-form" aria-label="基本信息" @submit.prevent="saveOverview">
            <div class="itinerary-section-heading"><h2>基本信息</h2><span>版本 {{ editor.state.snapshot.version }}</span></div>
            <div class="itinerary-field">
              <label for="editor-title">标题</label>
              <input id="editor-title" v-model.trim="overview.title" type="text" maxlength="100" required>
            </div>
            <div class="itinerary-form__grid">
              <div class="itinerary-field"><label for="editor-start">开始</label><input id="editor-start" v-model="overview.startDate" type="date" required></div>
              <div class="itinerary-field"><label for="editor-end">结束</label><input id="editor-end" v-model="overview.endDate" type="date" required></div>
            </div>
            <div class="itinerary-field"><label for="editor-zone">时区</label><input id="editor-zone" v-model.trim="overview.timeZone" type="text" required></div>
            <div class="itinerary-field"><label for="editor-currency">币种</label><input id="editor-currency" v-model.trim="overview.baseCurrency" type="text" maxlength="3" required></div>
            <button class="travel-secondary-button" type="submit" :disabled="mutationsDisabled">保存基本信息</button>
          </form>

          <form class="travel-panel itinerary-compact-form" aria-label="目的地" @submit.prevent="saveDestinations">
            <div class="itinerary-section-heading"><h2>目的地</h2><span>首项为主目的地</span></div>
            <div v-for="(destination, index) in destinations" :key="destination.id || index" class="itinerary-destination-edit">
              <label :for="`editor-destination-${index}`">目的地 {{ index + 1 }}</label>
              <div>
                <input :id="`editor-destination-${index}`" v-model.trim="destination.name" type="text" maxlength="100" required>
                <button v-if="destinations.length > 1" type="button" :disabled="mutationsDisabled" :aria-label="`移除目的地 ${destination.name || index + 1}`" @click="destinations.splice(index, 1)">移除</button>
              </div>
            </div>
            <div class="itinerary-inline-actions">
              <button class="itinerary-text-button" type="button" :disabled="mutationsDisabled" @click="addDestination">添加目的地</button>
              <button class="travel-secondary-button" type="submit" :disabled="mutationsDisabled">保存目的地</button>
            </div>
          </form>

          <section class="travel-panel itinerary-compact-form" aria-labelledby="lifecycle-title">
            <div class="itinerary-section-heading"><h2 id="lifecycle-title">行程状态</h2></div>
            <p v-if="editor.state.snapshot.suggestedStatus" class="itinerary-suggestion">建议下一步：{{ statusLabel(editor.state.snapshot.suggestedStatus) }}</p>
            <div class="itinerary-transition-list">
              <button
                v-for="status in editor.state.snapshot.allowedTransitions"
                :key="status"
                class="travel-secondary-button"
                type="button"
                :disabled="mutationsDisabled"
                :aria-label="`将状态改为 ${status}`"
                @click="changeStatus(status)"
              >{{ statusLabel(status) }}</button>
            </div>
          </section>
        </aside>

        <section class="itinerary-days" aria-label="每日安排">
          <div class="itinerary-days__heading">
            <div><p class="travel-page__eyebrow">DAILY PLAN</p><h2>每天的安排</h2></div>
            <span>{{ totalItems }} 项安排</span>
          </div>
          <article v-for="(day, dayIndex) in editor.state.snapshot.days" :key="day.id" class="travel-panel itinerary-day">
            <header class="itinerary-day__header">
              <div><span>第 {{ dayIndex + 1 }} 天</span><h3>{{ formatDate(day.date) }}</h3></div>
              <button class="itinerary-text-button" type="button" :disabled="mutationsDisabled" @click="startAdd(day.id)">添加安排</button>
            </header>
            <ol v-if="day.items.length" class="itinerary-items">
              <li
                v-for="(item, index) in day.items"
                :key="item.id"
                class="itinerary-item"
                :draggable="!mutationsDisabled"
                @dragstart="draggedItemId = item.id"
                @dragover.prevent
                @drop="dropBefore(day, item.id)"
              >
                <div class="itinerary-item__time">{{ item.startTime ? `${item.startTime}–${item.endTime}` : '未定时间' }}</div>
                <div class="itinerary-item__body"><strong>{{ item.title }}</strong><span v-if="item.placeName">{{ item.placeName }}</span><p v-if="item.notes">{{ item.notes }}</p></div>
                <div class="itinerary-item__actions">
                  <button type="button" :disabled="mutationsDisabled || index === 0" :aria-label="`上移 ${item.title}`" :data-item-id="item.id" data-move="up" @click="moveItem(day, index, -1)">上移</button>
                  <button type="button" :disabled="mutationsDisabled || index === day.items.length - 1" :aria-label="`下移 ${item.title}`" :data-item-id="item.id" data-move="down" @click="moveItem(day, index, 1)">下移</button>
                  <button type="button" :disabled="mutationsDisabled" :aria-label="`编辑 ${item.title}`" @click="startEdit(item)">编辑</button>
                  <button class="is-danger" type="button" :disabled="mutationsDisabled" :aria-label="`删除 ${item.title}`" @click="removeItem(item)">删除</button>
                </div>
              </li>
            </ol>
            <div v-else class="itinerary-day__empty">这一天还没有安排，先留给临时起意也很好。</div>
          </article>

          <form v-if="itemForm.open" class="travel-panel itinerary-item-form" aria-label="安排项" @submit.prevent="saveItem">
            <div class="itinerary-section-heading"><h2>{{ itemForm.itemId ? '编辑安排' : '新增安排' }}</h2><button type="button" aria-label="关闭安排表单" @click="closeItemForm">关闭</button></div>
            <div class="itinerary-form__grid">
              <div class="itinerary-field itinerary-field--wide"><label for="item-title">安排标题</label><input id="item-title" v-model.trim="itemForm.title" type="text" maxlength="120" required></div>
              <div class="itinerary-field"><label for="item-day">日期</label><select id="item-day" v-model="itemForm.dayId"><option v-for="day in editor.state.snapshot.days" :key="day.id" :value="day.id">{{ formatDate(day.date) }}</option></select></div>
              <div class="itinerary-field"><label for="item-place">地点</label><input id="item-place" v-model.trim="itemForm.placeName" type="text" maxlength="200"></div>
              <div class="itinerary-field"><label for="item-start">开始时间</label><input id="item-start" v-model="itemForm.startTime" type="time"></div>
              <div class="itinerary-field"><label for="item-end">结束时间</label><input id="item-end" v-model="itemForm.endTime" type="time"></div>
              <div class="itinerary-field itinerary-field--wide"><label for="item-notes">备注</label><textarea id="item-notes" v-model.trim="itemForm.notes" maxlength="2000" rows="3" /></div>
            </div>
            <div class="itinerary-form__actions"><button class="travel-primary-button" type="submit" :disabled="mutationsDisabled">保存安排</button></div>
          </form>
        </section>
      </section>
    </template>
  </main>
</template>

<script setup>
import { computed, inject, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { createItineraryEditor } from './itineraryEditor.js'
import { formatDate, formatDateRange } from './itineraryFormatters.js'
import { itineraryHttp, itineraryHttpKey } from './itineraryHttp.js'
import './itinerary.css'

const api = inject(itineraryHttpKey, itineraryHttp)
const route = useRoute()
const loading = ref(true)
const loadError = ref('')
const editor = ref(null)
const operationError = ref(null)
const draggedItemId = ref(null)
const refreshing = ref(false)
const projectionError = ref(false)
const overview = reactive({ title: '', startDate: '', endDate: '', timeZone: '', baseCurrency: '' })
const destinations = reactive([])
const blankItem = () => ({ open: false, itemId: null, dayId: '', title: '', placeName: '', startTime: '', endTime: '', notes: '', estimatedCost: null })
const itemForm = reactive(blankItem())
const labels = { DRAFT: '草稿', PLANNED: '已计划', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消', ARCHIVED: '已归档' }
const statusLabel = (status) => labels[status] || status
const totalItems = computed(() => editor.value?.state.snapshot.days.reduce((sum, day) => sum + day.items.length, 0) || 0)
const mutationsDisabled = computed(() => projectionError.value || refreshing.value || ['saving', 'error', 'conflict'].includes(editor.value?.state.status))
const saveLabel = computed(() => refreshing.value ? '正在同步…' : ({ idle: '尚无更改', saving: '保存中…', saved: '已保存', error: '保存失败', conflict: '存在版本冲突' }[editor.value?.state.status] || ''))
const operationErrorTitle = computed(() => {
  if (editor.value?.state.status === 'conflict') return '行程已在其他位置更新'
  if (projectionError.value) return '更改已保存，但最新内容未加载'
  return '这次更改没有保存'
})

const syncForms = () => {
  const snapshot = editor.value.state.snapshot
  Object.assign(overview, { title: snapshot.title, startDate: snapshot.startDate, endDate: snapshot.endDate, timeZone: snapshot.timeZone, baseCurrency: snapshot.baseCurrency })
  destinations.splice(0, destinations.length, ...snapshot.destinations.map((item) => ({ ...item })))
}
const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const snapshot = await api.get(String(route.params.itineraryId))
    editor.value = createItineraryEditor({ initialSnapshot: snapshot, api, uuid: () => crypto.randomUUID() })
    syncForms()
  } catch (error) {
    loadError.value = error.message || '暂时无法打开行程。'
  } finally { loading.value = false }
}
const run = async (operation) => {
  operationError.value = null
  projectionError.value = false
  try { return await operation } catch (error) { operationError.value = error; return null }
}
const reloadProjection = async () => {
  refreshing.value = true
  try {
    await editor.value.reload()
    syncForms()
    projectionError.value = false
  } catch (error) {
    operationError.value = error
    projectionError.value = true
  } finally {
    refreshing.value = false
  }
}
const saveOverview = async () => {
  if (mutationsDisabled.value) return
  if (await run(editor.value.updateOverview({ ...overview }))) await reloadProjection()
}
const addDestination = () => destinations.push({ name: '', countryCode: 'CN', timeZone: overview.timeZone })
const saveDestinations = async () => {
  if (mutationsDisabled.value) return
  if (await run(editor.value.replaceDestinations(destinations.map((item) => ({ ...item }))))) await reloadProjection()
}
const changeStatus = async (status) => {
  if (mutationsDisabled.value) return
  if (await run(editor.value.transition(status))) await reloadProjection()
}
const startAdd = (dayId) => { if (!mutationsDisabled.value) Object.assign(itemForm, blankItem(), { open: true, dayId }) }
const startEdit = (item) => { if (!mutationsDisabled.value) Object.assign(itemForm, blankItem(), { ...item, open: true, startTime: item.startTime || '', endTime: item.endTime || '' }) }
const closeItemForm = () => Object.assign(itemForm, blankItem())
const saveItem = async () => {
  if (mutationsDisabled.value) return
  const payload = { dayId: itemForm.dayId, title: itemForm.title, placeName: itemForm.placeName || null, startTime: itemForm.startTime || null, endTime: itemForm.endTime || null, notes: itemForm.notes || null, estimatedCost: itemForm.estimatedCost }
  const operation = itemForm.itemId ? editor.value.updateItem(itemForm.itemId, payload) : editor.value.addItem(payload)
  if (await run(operation)) closeItemForm()
}
const removeItem = async (item) => {
  if (mutationsDisabled.value) return
  if (!window.confirm(`确定删除“${item.title}”吗？此操作会保存到行程。`)) return
  await run(editor.value.deleteItem(item.id))
}
const moveItem = async (day, index, offset) => {
  if (mutationsDisabled.value) return
  const moved = day.items[index]
  const ids = day.items.map((item) => item.id)
  const [id] = ids.splice(index, 1)
  ids.splice(index + offset, 0, id)
  await run(editor.value.reorderItems(day.id, ids))
  await nextTick()
  document.querySelector(`[data-item-id="${moved.id}"][data-move="${offset > 0 ? 'up' : 'down'}"]`)?.focus()
}
const dropBefore = async (day, targetId) => {
  if (mutationsDisabled.value || !draggedItemId.value || draggedItemId.value === targetId) return
  const ids = day.items.map((item) => item.id).filter((id) => id !== draggedItemId.value)
  ids.splice(ids.indexOf(targetId), 0, draggedItemId.value)
  draggedItemId.value = null
  await run(editor.value.reorderItems(day.id, ids))
}
const reload = async () => {
  operationError.value = null
  refreshing.value = true
  try {
    await editor.value.reload()
    syncForms()
    projectionError.value = false
  } catch (error) {
    operationError.value = error
    projectionError.value = true
  } finally {
    refreshing.value = false
  }
}
const retry = async () => {
  operationError.value = null
  await run(editor.value.retryFailed())
}
onMounted(load)
</script>

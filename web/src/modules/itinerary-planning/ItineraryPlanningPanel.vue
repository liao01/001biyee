<template>
  <aside class="travel-panel itinerary-planning-panel" aria-labelledby="planning-panel-title">
    <header class="planning-panel-header">
      <div>
        <p class="travel-page__eyebrow">AI ITINERARY</p>
        <h2 id="planning-panel-title">AI 行程规划</h2>
        <p>先保存结构化需求，再预览并确认建议。AI 不会直接修改你的行程。</p>
      </div>
      <button class="itinerary-text-button" type="button" aria-label="关闭 AI 行程规划" @click="$emit('close')">关闭</button>
    </header>

    <div v-if="loading" class="planning-loading" role="status">正在恢复规划记录…</div>
    <template v-else>
      <div v-if="safeError" class="planning-error" role="alert">
        <strong>{{ safeError.title }}</strong>
        <p>{{ safeError.message }}</p>
        <button v-if="safeError.retry" class="travel-secondary-button" type="button" @click="retryAction">重试</button>
      </div>
      <div v-if="notice" class="planning-notice" role="status" aria-live="polite">{{ notice }}</div>
      <div v-if="state.status === 'generating'" class="planning-generating" role="status" aria-live="polite" aria-busy="true">
        <span class="planning-spinner" aria-hidden="true" />
        <div><strong>正在结合参考知识生成建议</strong><p>通常需要几秒钟，请保持此页面打开。</p></div>
      </div>

      <PlanningRequestForm :draft="draft" :busy="busy" @save="save" @generate="generate" />

      <div v-if="state.proposal" class="planning-proposal-area">
        <div v-if="isExpired" class="planning-expired" role="status">
          <strong>行程已变化，请重新生成建议</strong>
          <p>你仍可以查看这份建议，但不能再写入当前版本。</p>
        </div>
        <PlanningDiff
          :proposal="state.proposal"
          :selected-keys="state.selectedOperationKeys"
          :disabled="busy || isExpired || state.proposal.status !== 'READY'"
          @select="planning.selectOperation"
          @select-all="selectAll"
        />
        <div class="planning-decision-actions">
          <button class="travel-secondary-button" type="button" :disabled="busy || state.proposal.status !== 'READY'" @click="reject">拒绝这份建议</button>
          <button class="travel-primary-button" type="button" aria-label="确认并写入行程" :disabled="busy || isExpired || state.proposal.status !== 'READY' || !state.selectedOperationKeys.size" @click="confirm">确认并写入行程</button>
        </div>
      </div>
    </template>
  </aside>
</template>

<script setup>
import { computed, inject, onMounted, reactive, ref, toRaw, watch } from 'vue'
import { createItineraryPlanning } from './itineraryPlanning.js'
import { itineraryPlanningHttp, itineraryPlanningHttpKey } from './itineraryPlanningHttp.js'
import PlanningDiff from './PlanningDiff.vue'
import PlanningRequestForm from './PlanningRequestForm.vue'
import './itineraryPlanning.css'

const props = defineProps({
  itinerary: { type: Object, required: true },
  itineraryApi: { type: Object, required: true },
})
const emit = defineEmits(['close', 'busy-change', 'confirmed'])
const api = inject(itineraryPlanningHttpKey, itineraryPlanningHttp)
const planning = createItineraryPlanning({
  itineraryId: props.itinerary.id,
  api,
  itineraryApi: props.itineraryApi,
  uuid: () => crypto.randomUUID(),
})
const state = planning.state
const loading = ref(true)
const notice = ref('')
const lastAction = ref(null)
const defaultDraft = () => ({
  startDate: props.itinerary.startDate,
  endDate: props.itinerary.endDate,
  budgetAmount: 0,
  budgetCurrency: props.itinerary.baseCurrency,
  partySize: 1,
  preferences: { pace: 'BALANCED', tags: [], notes: '' },
  destinations: props.itinerary.destinations.map((destination) => ({
    name: destination.name,
    countryCode: destination.countryCode,
    timeZone: destination.timeZone,
  })),
})
const draft = reactive(defaultDraft())
const busy = computed(() => ['saving', 'generating', 'confirming'].includes(state.status))
const isExpired = computed(() => state.proposal?.status === 'EXPIRED' || state.status === 'expired'
  || (state.proposal && state.proposal.comparedItineraryVersion !== state.proposal.baseItineraryVersion))

const errors = {
  PROVIDER_TIMEOUT: ['生成超时', 'AI 规划响应超时，请重试。'],
  PROVIDER_RATE_LIMITED: ['暂时无法生成', '请求较多，请稍后重试。'],
  PROVIDER_UNAVAILABLE: ['AI 规划暂不可用', '请检查网络或稍后重试。'],
  INVALID_CONTRACT: ['建议格式无效', 'AI 返回的建议无法安全解析，请重新生成。'],
  TIME_CONFLICT: ['建议未通过校验', '建议中存在时间冲突，请调整需求后重新生成。'],
  BUDGET_EXCEEDED: ['建议超出预算', '请提高预算或调整偏好后重新生成。'],
  INVALID_SELECTION: ['所选建议缺少依赖', '请保留提示的依赖项后再确认。'],
}
const safeError = computed(() => {
  if (!state.error) return null
  const [title, message] = errors[state.error.errorCode] || ['操作未完成', '请检查网络后重试，正式行程没有被修改。']
  return { title, message, retry: !['PROPOSAL_EXPIRED', 'VERSION_CONFLICT'].includes(state.error.errorCode) }
})

const hydrate = (source) => {
  if (!source) return
  Object.assign(draft, {
    startDate: source.startDate,
    endDate: source.endDate,
    budgetAmount: Number(source.budgetAmount),
    budgetCurrency: source.budgetCurrency,
    partySize: source.partySize,
    preferences: {
      pace: source.preferences?.pace || 'BALANCED',
      tags: [...(source.preferences?.tags || [])],
      notes: source.preferences?.notes || '',
    },
    destinations: source.destinations.map((destination) => ({ ...destination })),
  })
}
const normalizedDraft = () => ({
  startDate: draft.startDate,
  endDate: draft.endDate,
  budgetAmount: Number(draft.budgetAmount),
  budgetCurrency: draft.budgetCurrency.toUpperCase(),
  partySize: Number(draft.partySize),
  preferences: {
    pace: draft.preferences.pace,
    tags: [...draft.preferences.tags],
    notes: draft.preferences.notes,
  },
  destinations: draft.destinations.map((destination) => ({
    name: destination.name,
    countryCode: destination.countryCode.toUpperCase(),
    timeZone: destination.timeZone,
  })),
})
const save = async () => {
  notice.value = ''
  lastAction.value = save
  try {
    const saved = await planning.saveDraft(normalizedDraft())
    hydrate(saved)
    notice.value = '需求已保存。'
    return saved
  } catch { return null }
}
const generate = async () => {
  notice.value = ''
  lastAction.value = generate
  const saved = await save()
  if (!saved) return
  lastAction.value = generate
  try { await planning.generate() } catch { /* 安全错误由状态机分类展示 */ }
}
const confirm = async () => {
  notice.value = ''
  lastAction.value = confirm
  try {
    await planning.confirm()
    notice.value = '建议已确认，正式行程已刷新。'
    emit('confirmed', structuredClone(toRaw(state.itinerarySnapshot)))
  } catch { /* 保留选择并展示安全错误 */ }
}
const reject = async () => {
  notice.value = ''
  lastAction.value = reject
  try {
    await planning.reject()
    notice.value = '建议已拒绝，正式行程没有变化。'
  } catch { /* 保留建议并允许稳定重试 */ }
}
const retryAction = () => lastAction.value?.()
const selectAll = (selected) => {
  for (const operation of state.proposal?.operations || []) {
    planning.selectOperation(operation.operationKey, selected)
  }
}

watch(busy, (value) => emit('busy-change', value), { immediate: true })
onMounted(async () => {
  try {
    await planning.load()
    hydrate(state.request)
  } catch { /* 错误由面板展示 */ }
  finally { loading.value = false }
})
</script>

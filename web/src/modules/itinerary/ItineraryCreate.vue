<template>
  <main class="travel-page itinerary-page itinerary-page--narrow">
    <header class="travel-page__header">
      <div>
        <p class="travel-page__eyebrow">NEW ITINERARY</p>
        <h1 class="travel-page__title">创建旅行行程</h1>
        <p class="travel-page__subtitle">先确定时间和目的地，创建后再安排每天的细节。</p>
      </div>
    </header>

    <form class="travel-panel itinerary-form" novalidate @submit.prevent="submit">
      <div v-if="errors.length" ref="errorSummary" class="itinerary-error-summary" role="alert" tabindex="-1">
        <strong>请完善以下信息</strong>
        <ul><li v-for="error in errors" :key="error.id"><a :href="`#${error.field}`">{{ error.message }}</a></li></ul>
      </div>

      <fieldset>
        <legend>行程概览</legend>
        <div class="itinerary-field itinerary-field--wide">
          <label for="itinerary-title">行程标题 <span aria-hidden="true">*</span></label>
          <input id="itinerary-title" v-model.trim="form.title" type="text" maxlength="100" :aria-invalid="hasError('title')" aria-describedby="itinerary-title-help itinerary-title-error">
          <small id="itinerary-title-help">例如“杭州周末”或“川西七日自驾”。</small>
          <small v-if="hasError('title')" id="itinerary-title-error" class="itinerary-field__error">请输入行程标题</small>
        </div>
        <div class="itinerary-form__grid">
          <div class="itinerary-field">
            <label for="itinerary-start-date">开始日期 <span aria-hidden="true">*</span></label>
            <input id="itinerary-start-date" v-model="form.startDate" type="date">
          </div>
          <div class="itinerary-field">
            <label for="itinerary-end-date">结束日期 <span aria-hidden="true">*</span></label>
            <input id="itinerary-end-date" v-model="form.endDate" type="date">
          </div>
          <div class="itinerary-field">
            <label for="itinerary-time-zone">行程时区</label>
            <select id="itinerary-time-zone" v-model="form.timeZone">
              <option value="Asia/Shanghai">中国标准时间（上海）</option>
              <option value="Asia/Tokyo">日本标准时间（东京）</option>
              <option value="Europe/Paris">中欧时间（巴黎）</option>
              <option value="America/New_York">美国东部时间（纽约）</option>
            </select>
          </div>
          <div class="itinerary-field">
            <label for="itinerary-currency">基准币种</label>
            <select id="itinerary-currency" v-model="form.baseCurrency">
              <option value="CNY">人民币 CNY</option>
              <option value="JPY">日元 JPY</option>
              <option value="EUR">欧元 EUR</option>
              <option value="USD">美元 USD</option>
            </select>
          </div>
        </div>
      </fieldset>

      <fieldset>
        <legend>目的地</legend>
        <p class="itinerary-form__hint">第一项会作为主目的地显示，可以在创建后继续调整顺序。</p>
        <div v-for="(destination, index) in form.destinations" :key="index" class="itinerary-destination-row">
          <div class="itinerary-field">
            <label :for="`destination-name-${index}`">目的地 {{ index + 1 }} <span aria-hidden="true">*</span></label>
            <input :id="`destination-name-${index}`" v-model.trim="destination.name" type="text" maxlength="100">
          </div>
          <button v-if="form.destinations.length > 1" class="itinerary-text-button itinerary-text-button--danger" type="button" :aria-label="`移除目的地 ${index + 1}`" @click="form.destinations.splice(index, 1)">移除</button>
        </div>
        <button class="travel-secondary-button" type="button" @click="addDestination">添加目的地</button>
      </fieldset>

      <div class="itinerary-form__actions">
        <RouterLink class="travel-secondary-button" to="/itineraries">取消</RouterLink>
        <button class="travel-primary-button" type="submit" :disabled="submitting">
          {{ submitting ? '正在创建…' : '创建并开始规划' }}
        </button>
      </div>
    </form>
  </main>
</template>

<script setup>
import { inject, nextTick, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { itineraryHttp, itineraryHttpKey } from './itineraryHttp.js'
import './itinerary.css'

const api = inject(itineraryHttpKey, itineraryHttp)
const router = useRouter()
const form = reactive({
  title: '', startDate: '', endDate: '', timeZone: 'Asia/Shanghai', baseCurrency: 'CNY',
  destinations: [{ name: '', countryCode: 'CN', timeZone: 'Asia/Shanghai' }],
})
const errors = ref([])
const submitting = ref(false)
const errorSummary = ref(null)
const hasError = (id) => errors.value.some((error) => error.id === id)
const addDestination = () => form.destinations.push({ name: '', countryCode: 'CN', timeZone: form.timeZone })
const validate = () => {
  const next = []
  if (!form.title) next.push({ id: 'title', field: 'itinerary-title', message: '请输入行程标题' })
  if (!form.startDate) next.push({ id: 'startDate', field: 'itinerary-start-date', message: '请选择开始日期' })
  if (!form.endDate) next.push({ id: 'endDate', field: 'itinerary-end-date', message: '请选择结束日期' })
  if (form.startDate && form.endDate && form.endDate < form.startDate) next.push({ id: 'range', field: 'itinerary-end-date', message: '结束日期不能早于开始日期' })
  if (form.destinations.some((destination) => !destination.name)) next.push({ id: 'destination', field: 'destination-name-0', message: '请填写每个目的地' })
  errors.value = next
  return next.length === 0
}
const submit = async () => {
  if (!validate()) {
    await nextTick()
    errorSummary.value?.focus()
    return
  }
  submitting.value = true
  try {
    const result = await api.create({
      commandId: crypto.randomUUID(), expectedVersion: 0,
      payload: {
        ...form,
        destinations: form.destinations.map((item) => ({ ...item, timeZone: form.timeZone })),
      },
    })
    await router.push(`/itineraries/${result.itineraryId}`)
  } catch (error) {
    errors.value = [{ id: 'server', field: 'itinerary-title', message: error.message || '创建失败，请重试' }]
    await nextTick()
    errorSummary.value?.focus()
  } finally {
    submitting.value = false
  }
}
</script>

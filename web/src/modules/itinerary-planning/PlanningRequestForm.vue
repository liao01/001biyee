<template>
  <form class="planning-request-form" aria-label="AI 行程规划需求" @submit.prevent="$emit('save')">
    <fieldset :disabled="busy">
      <legend>1. 规划需求</legend>
      <p class="planning-help">这些信息会作为结构化条件发送给 AI，不会把行程直接写入正式安排。</p>

      <div class="planning-form-grid">
        <div class="itinerary-field">
          <label for="planning-start-date">开始日期</label>
          <input id="planning-start-date" v-model="draft.startDate" type="date" required>
        </div>
        <div class="itinerary-field">
          <label for="planning-end-date">结束日期</label>
          <input id="planning-end-date" v-model="draft.endDate" type="date" required>
        </div>
        <div class="itinerary-field">
          <label for="planning-budget">总预算</label>
          <input id="planning-budget" v-model.number="draft.budgetAmount" type="number" min="0" step="0.01" required>
        </div>
        <div class="itinerary-field">
          <label for="planning-currency">币种</label>
          <input id="planning-currency" v-model.trim="draft.budgetCurrency" type="text" maxlength="3" required>
        </div>
        <div class="itinerary-field">
          <label for="planning-party-size">同行人数</label>
          <input id="planning-party-size" v-model.number="draft.partySize" type="number" min="1" max="50" required>
        </div>
        <div class="itinerary-field">
          <label for="planning-pace">行程节奏</label>
          <select id="planning-pace" v-model="draft.preferences.pace">
            <option value="RELAXED">轻松</option>
            <option value="BALANCED">均衡</option>
            <option value="FAST">紧凑</option>
          </select>
        </div>
      </div>

      <fieldset class="planning-preferences">
        <legend>偏好</legend>
        <div class="planning-chips">
          <label v-for="tag in preferenceTags" :key="tag.value" :for="`planning-preference-${tag.value}`">
            <input :id="`planning-preference-${tag.value}`" v-model="draft.preferences.tags" type="checkbox" :value="tag.value">
            <span>{{ tag.label }}</span>
          </label>
        </div>
      </fieldset>

      <div class="itinerary-field">
        <label for="planning-notes">补充偏好</label>
        <textarea id="planning-notes" v-model.trim="draft.preferences.notes" rows="3" maxlength="1000" placeholder="例如：不早起、步行不要太多、希望留出拍照时间" />
      </div>

      <fieldset class="planning-destinations">
        <legend>目的地</legend>
        <div v-for="(destination, index) in draft.destinations" :key="index" class="planning-destination-row">
          <div class="itinerary-field planning-destination-row__name">
            <label :for="`planning-destination-${index}`">目的地 {{ index + 1 }}</label>
            <input :id="`planning-destination-${index}`" v-model.trim="destination.name" type="text" maxlength="100" required>
          </div>
          <div class="itinerary-field">
            <label :for="`planning-country-${index}`">国家/地区</label>
            <input :id="`planning-country-${index}`" v-model.trim="destination.countryCode" type="text" maxlength="2" required>
          </div>
          <div class="itinerary-field">
            <label :for="`planning-zone-${index}`">时区</label>
            <input :id="`planning-zone-${index}`" v-model.trim="destination.timeZone" type="text" required>
          </div>
          <button v-if="draft.destinations.length > 1" class="itinerary-text-button itinerary-text-button--danger" type="button" :aria-label="`移除规划目的地 ${index + 1}`" @click="draft.destinations.splice(index, 1)">移除</button>
        </div>
        <button class="itinerary-text-button" type="button" @click="addDestination">添加目的地</button>
      </fieldset>
    </fieldset>

    <div class="planning-form-actions">
      <button class="travel-secondary-button" type="submit" aria-label="保存 AI 规划需求" :disabled="busy">保存需求</button>
      <button class="travel-primary-button" type="button" aria-label="生成 AI 行程建议" :disabled="busy" @click="$emit('generate')">生成建议</button>
    </div>
  </form>
</template>

<script setup>
const props = defineProps({
  draft: { type: Object, required: true },
  busy: { type: Boolean, default: false },
})
defineEmits(['save', 'generate'])

const preferenceTags = [
  { value: 'CULTURE', label: '人文' }, { value: 'FOOD', label: '美食' },
  { value: 'NATURE', label: '自然' }, { value: 'SHOPPING', label: '购物' },
  { value: 'FAMILY', label: '亲子' }, { value: 'NIGHTLIFE', label: '夜生活' },
  { value: 'PHOTOGRAPHY', label: '摄影' }, { value: 'OUTDOORS', label: '户外' },
]

const addDestination = () => props.draft.destinations.push({
  name: '', countryCode: 'CN', timeZone: props.draft.destinations[0]?.timeZone || 'Asia/Shanghai',
})
</script>

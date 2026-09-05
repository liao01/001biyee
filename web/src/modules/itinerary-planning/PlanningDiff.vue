<template>
  <section class="planning-diff" aria-labelledby="planning-diff-title">
    <div class="planning-section-heading">
      <div><span>第 2 步</span><h3 id="planning-diff-title">检查建议差异</h3></div>
      <div class="planning-selection-actions">
        <button type="button" aria-label="选择全部建议" @click="$emit('select-all', true)">全选</button>
        <button type="button" aria-label="取消选择全部建议" @click="$emit('select-all', false)">全不选</button>
      </div>
    </div>

    <p class="planning-summary">{{ proposal.summary }}</p>
    <p v-if="proposal.knowledgeReferenceIds?.length" class="planning-knowledge-note">参考知识已参与生成。请仍以景区、交通和商家最新信息为准。</p>

    <section v-for="group in groupedOperations" :key="group.date" class="planning-day-group">
      <h4>{{ formatDate(group.date) }}</h4>
      <article v-for="operation in group.operations" :key="operation.operationKey" class="planning-operation">
        <label :for="`planning-operation-${operation.operationKey}`" class="planning-operation__choice">
          <input
            :id="`planning-operation-${operation.operationKey}`"
            type="checkbox"
            :checked="selectedKeys.has(operation.operationKey)"
            :disabled="disabled"
            @change="$emit('select', operation.operationKey, $event.target.checked)"
          >
          <span><strong>{{ operationLabel(operation.type) }}</strong>{{ operation.summary }}</span>
        </label>
        <p v-if="operation.dependencies?.length" class="planning-dependency">选择此项会同时保留 {{ operation.dependencies.length }} 项依赖</p>

        <div v-if="operation.type === 'UPDATE_ITEM'" class="planning-before-after">
          <ItemValue label="修改前" :item="operation.beforeItem" />
          <ItemValue label="修改后" :item="operation.afterItem" />
        </div>
        <div v-else-if="operation.type === 'ADD_ITEM'" class="planning-before-after planning-before-after--single">
          <ItemValue label="新增安排" :item="operation.afterItem" />
        </div>
        <div v-else-if="operation.type === 'DELETE_ITEM'" class="planning-before-after planning-before-after--single">
          <ItemValue label="将删除" :item="operation.beforeItem" />
        </div>
        <div v-else class="planning-order-diff">
          <span>调整前 {{ orderLabel(operation.beforeItemReferences) }}</span>
          <span>调整后 {{ orderLabel(operation.afterItemReferences) }}</span>
        </div>
      </article>
    </section>
  </section>
</template>

<script>
import { defineComponent, h } from 'vue'

const ItemValue = defineComponent({
  props: { label: String, item: Object },
  setup(props) {
    return () => h('div', { class: 'planning-item-value' }, [
      h('span', props.label),
      h('strong', props.item?.title || '无'),
      props.item?.placeName ? h('small', props.item.placeName) : null,
      props.item?.startTime ? h('small', `${props.item.startTime}–${props.item.endTime || ''}`) : null,
    ])
  },
})

export default {
  components: { ItemValue },
  props: {
    proposal: { type: Object, required: true },
    selectedKeys: { type: Object, required: true },
    disabled: { type: Boolean, default: false },
  },
  emits: ['select', 'select-all'],
  computed: {
    groupedOperations() {
      const groups = new Map()
      for (const operation of this.proposal.operations || []) {
        const date = operation.targetDate || operation.afterItem?.date || operation.beforeItem?.date || '未指定日期'
        if (!groups.has(date)) groups.set(date, [])
        groups.get(date).push(operation)
      }
      return [...groups].map(([date, operations]) => ({ date, operations }))
    },
  },
  methods: {
    operationLabel(type) {
      return { ADD_ITEM: '新增', UPDATE_ITEM: '修改', DELETE_ITEM: '删除', REORDER_DAY_ITEMS: '排序' }[type] || type
    },
    formatDate(value) {
      if (value === '未指定日期') return value
      return new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' })
        .format(new Date(`${value}T00:00:00`))
    },
    orderLabel(references = []) {
      return references.map((reference) => reference.existingItemId || `建议 ${reference.addedByOperationKey}`).join(' → ') || '无'
    },
  },
}
</script>

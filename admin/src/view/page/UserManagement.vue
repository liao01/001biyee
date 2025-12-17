<template>
  <h1>用户管理</h1>
  <a-table
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      row-key="id"
      :loading="loading"
      @change="handleTableChange"
  >
    <template #bodyCell="{ column, record }">
      <!-- 用户名 -->
      <template v-if="column.key === 'loginName'">
        <a>{{ record.loginName }}</a>
      </template>

      <!-- 操作 -->
      <template v-else-if="column.key === 'action'">
        <a @click="resetPwd(record)">重置密码</a>
        <a-divider type="vertical" />
        <a @click="deleteUser(record)">删除</a>
      </template>
    </template>
  </a-table>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import axios from 'axios'

// 如果你 main.js 里没配 baseURL，这里一定要写全
axios.defaults.baseURL = 'http://localhost:8080/lyw'

const loading = ref(false)
const data = ref([])

const pagination = reactive({
  current: 1,
  pageSize: 1,
  total: 0,
  showTotal: total => `共 ${total} 条`
})

const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
    key: 'id'
  },
  {
    title: '用户名',
    dataIndex: 'loginName',
    key: 'loginName'
  },
  {
    title: '操作',
    key: 'action'
  }
]

const loadData = async () => {
  loading.value = true
  try {
    const res = await axios.get('/admin/member/query', {
      params: {
        page: pagination.current,
        size: pagination.pageSize
      }
    })

    console.log('接口返回：', res)

    // ✅ 原生 axios 正确判断
    if (res.data && res.data.success) {
      data.value = res.data.content.page
      pagination.total = res.data.content.total
    } else {
      message.error(res.data?.message || '查询失败')
    }
  } catch (e) {
    console.error(e)
    message.error('接口请求异常')
  } finally {
    loading.value = false
  }
}

const handleTableChange = (pager) => {
  pagination.current = pager.current
  pagination.pageSize = pager.pageSize
  loadData()
}

const resetPwd = (record) => {
  message.info(`重置用户 ${record.loginName} 密码`)
}

const deleteUser = (record) => {
  message.warning(`删除用户 ${record.loginName}`)
}

onMounted(() => {
  loadData()
})
</script>



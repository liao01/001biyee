<template>
  <h1>欢迎来到旅游分享后台管理系统</h1>
  <hr>
  <br>
  <a-row>
    <a-col :span="12">
      <a-statistic title="当前在线人数" :value=" account.onlineCount " style="margin-right: 50px" />
    </a-col>
    <a-col :span="12">
      <a-statistic title="日活跃用户" :value=" account.dau " style="margin-right: 50px" />
    </a-col>
  </a-row>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { message } from 'ant-design-vue'

const account = ref({
  onlineCount: 0,
  dau: 0
})

onMounted(() => {
  axios.get('http://localhost:8080/lyw/admin/report/query-statistic')
      .then(response => {
        const data = response.data
        if (data.success) {
          account.value.onlineCount = data.content.onlineCount
        } else {
          message.error(data.message)
        }
      })
  axios.get('http://localhost:8080/lyw/admin/report/dau')
      .then(response => {
        const data = response.data
        if (data.success) {
          account.value.dau = data.content.dau
        } else {
          message.error(data.message)
        }
      })
})
</script>

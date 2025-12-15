<template>
  <h1>欢迎来到旅游分享后台管理系统</h1>
  <br>
  <a-row>
    <a-col :span="12">
      <a-statistic title="当前在线人数" :value=" account.onlineCount " style="margin-right: 50px" />
    </a-col>
  </a-row>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { message } from 'ant-design-vue'

const account = ref({})

onMounted(() => {
  axios.get('http://localhost:8080/lyw/admin/report/query-statistic')
      .then(response => {
        const data = response.data
        if (data.success) {
          account.value = data.content
        } else {
          message.error(data.message)
        }
      })
})
</script>

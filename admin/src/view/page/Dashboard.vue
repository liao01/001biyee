<template>
  <div class="dashboard-container">
    <div class="welcome-header">
      <h1>欢迎来到旅游分享后台管理系统</h1>
      <p class="subtitle">实时数据概览与统计分析</p>
    </div>

    <a-row :gutter="[16, 16]" class="statistic-row">
      <a-col :xs="24" :sm="12" :md="8" :lg="4">
        <a-card hoverable class="stat-card online-status">
          <a-statistic title="当前在线人数" :value="account.onlineCount" :value-style="{ color: '#3f51b5' }" />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="8" :lg="4">
        <a-card hoverable class="stat-card">
          <a-statistic title="今日活跃用户" :value="account.dau" />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="8" :lg="4">
        <a-card hoverable class="stat-card">
          <a-statistic title="今日发帖总数" :value="account.postDayCount" />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="8" :lg="4">
        <a-card hoverable class="stat-card">
          <a-statistic title="发帖总数" :value="account.postCount || 0" />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="8" :lg="4">
        <a-card hoverable class="stat-card">
          <a-statistic title="用户总数" :value="account.totalCount" />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="8" :lg="4">
        <a-card hoverable class="stat-card highlight">
          <a-statistic title="今日注册数" :value="account.todayNewUsers" :value-style="{ color: '#cf1322' }" />
        </a-card>
      </a-col>
    </a-row>

    <a-card class="chart-card" :bordered="false">
      <div id="post30DayCountList-col">
        <div id="post30DayCountList" style="width: 100%; height: 400px;"></div>
      </div>
    </a-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { message } from 'ant-design-vue'

const account = ref({
  onlineCount: 0,
  dau: 0,
  postDayCount:  0,
  postCount: 0,
  totalCount: 0,
  todayNewUsers: 0
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
  axios.get('http://localhost:8080/lyw/admin/report/postDayCount')
      .then(response => {
        const data = response.data
        if (data.success) {
          account.value.postDayCount = data.content.postDayCount
        } else {
          message.error(data.message)
        }
      })
  axios.get('http://localhost:8080/lyw/admin/report/postCount')
      .then(response => {
        const data = response.data
        if (data.success) {
          account.value.postCount = data.content.postCount
        } else {
          message.error(data.message)
        }
      })
  axios.get('http://localhost:8080/lyw/admin/report/UserCount')
      .then(response => {
        const data = response.data
        if (data.success) {
          account.value.totalCount = data.content.totalCount
        } else {
          message.error(data.message)
        }
      })
  axios.get('http://localhost:8080/lyw/admin/report/RegisterUserCount')
      .then(response => {
        const data = response.data
        if (data.success) {
          account.value.todayNewUsers = data.content.todayNewUsers
        } else {
          message.error(data.message)
        }
      })


  axios.get('http://localhost:8080/lyw/admin/report/postCount30Days')
      .then(response => {
        const data = response.data
        if (data.success) {
          account.value.selectDailyPostCountLast30Days = data.content.selectDailyPostCountLast30Days
          formatAndRender30Chart("post30DayCountList", "近30天发送帖子数量", account.value.selectDailyPostCountLast30Days);
        } else {
          message.error(data.message)
        }
      })
})


// ----------------- 图表显示 -----------------
const formatAndRender30Chart = (id, title, data30) => {
  let dates = [];
  let nums = [];
  for (let i = 0; i < data30.length; i++) {
    const record = data30[i];
    dates.push(record.date);
    nums.push(record.num);
  }
  console.log(dates);
  console.log(nums);
  render30Chart(id, title, dates, nums);
}

const render30Chart = (id, title, xAxisData, yAxisData) => {
  const dom = document.getElementById(id);
  if (!dom) return;
  const myChart = echarts.init(dom);
  const option = {
    title: { text: title },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: xAxisData },
    yAxis: { type: 'value' },
    series: [{ data: yAxisData, type: 'line', smooth: true }]
  };
  myChart.setOption(option);
};
</script>

<style scoped>
.dashboard-container {
  padding: 24px;
  background-color: #f0f2f5;
  min-height: 100vh;
}

.welcome-header {
  margin-bottom: 24px;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.welcome-header h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1a1a1a;
}

.subtitle {
  margin: 4px 0 0 0;
  color: #8c8c8c;
}

.statistic-row {
  margin-bottom: 24px;
}

.stat-card {
  border-radius: 8px;
  text-align: center;
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.chart-card {
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

/* 装饰性左边条 */
.online-status {
  border-left: 4px solid #1890ff;
}
.highlight {
  border-left: 4px solid #ff4d4f;
}
</style>
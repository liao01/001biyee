<template>
  <h1>欢迎来到旅游分享后台管理系统</h1>
  <hr>
  <br>
  <a-row>
    <a-col :span="12">
      <a-statistic title="当前在线人数" :value=" account.onlineCount " style="margin-right: 50px" />
    </a-col>
    <a-col :span="12">
      <a-statistic title="今日活跃用户" :value=" account.dau " style="margin-right: 50px" />
    </a-col>
    <a-col :span="12">
      <a-statistic title="今日发帖总数" :value=" account.postDayCount " style="margin-right: 50px" />
    </a-col>
    <a-col :span="12">
      <a-statistic title="发帖总数" :value=" account.postCount " style="margin-right: 50px" />
    </a-col>
    <a-col :span="12">
      <a-statistic title="用户数 " :value=" account.totalCount " style="margin-right: 50px" />
    </a-col>
  </a-row>
  <div id="post30DayCountList" style="width: 100%;height:250px;"></div>
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
  totalCount: 0
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

const render30Chart = (id, title, xAxios, yAxios) => {
  // 发布生产后出现问题：切到别的页面，再切回首页，报表显示不出来
  // 解决方法：把原来的id=registerCount的区域清空，重新初始化
  const dom = document.getElementById(id + '-col');
  if (dom) {
    dom.innerHTML = '<div id="' + id + '" style="width: 100%;height:250px;"></div>';
  }
  const myChart = echarts.init(document.getElementById(id));
  const option = {
    title: {
      text: title,
    },
    xAxis: {
      data: xAxios
    },
    yAxis: {},
    series: [{
      data: yAxios,
      type: 'line'
    }]
  };
  myChart.setOption(option);
};
</script>

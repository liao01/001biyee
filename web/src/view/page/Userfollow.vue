<template>
  <div class="travel-page follow-page">
    <header class="travel-page__header">
      <div>
        <h1 class="travel-page__title">粉丝数据</h1>
        <p class="travel-page__subtitle">了解哪些旅行内容正在建立新的连接。</p>
      </div>
    </header>
    <section class="travel-panel follow-page__panel">
    <div class="card-header">
      <span class="card-title"><UserOutlined /> 数据概览</span>
    </div>

    <a-row :gutter="[24, 24]" class="stat-row">
      <a-col :xs="23" :sm="7">
        <a-statistic
            title="粉丝总数"
            :value="statistic.countFollowers || 0"
            value-style="{ color: '#3f8600', fontWeight: 'bold', fontSize: '24px' }"
        />
      </a-col>
      <a-col :xs="24" :sm="8">
        <a-statistic
            title="昨日涨粉数量"
            :value="statistic.countYesterdayNew || 0"
            value-style="{ color: '#1890ff', fontWeight: 'bold', fontSize: '24px' }"
        />
      </a-col>
      <a-col :xs="24" :sm="8">
        <a-statistic
            title="昨日掉粉数量"
            :value="statistic.countYesterdayUn || 0"
            value-style="{ color: '#ff4d4f', fontWeight: 'bold', fontSize: '24px' }"
        />
      </a-col>
    </a-row>

    <div class="chart-container">
      <div id="getUserFollowTrendLast30Days" style="width: 100%; height: 300px;"></div>
    </div>
    <br>
    <br>
    <div class="card-header">
      <span class="card-title"><TeamOutlined /> 粉丝列表</span>
    </div>
    <a-table
        :dataSource="getFollowingListByUserId"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        @change="handleTableChange"
        rowKey="followId"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'username'">
          {{ record.username }}
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ record.createTime ? formatDate(record.createTime) : '未记录' }}
        </template>
      </template>
    </a-table>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import axios from "axios";
import { notification } from "ant-design-vue";
import { UserOutlined } from '@ant-design/icons-vue';
import { BASE_URL } from "../../utils/baseUrl";

const statistic = ref({});
const loading = ref(false);

const columns = [
  { title: '用户名', dataIndex: 'username' },
  { title: '关注时间', dataIndex: 'createTime' }
];

// 格式化时间
const formatDate = (dateStr) => {
  const date = new Date(dateStr);
  return date.toLocaleString();
};

const fetchStatistic = async () => {
  loading.value = true;
  try {
    const response = await axios.get(BASE_URL+"/lyw/web/userfollow/query-statistic");
    loading.value = false;
    if (response.data.success) {
      statistic.value = response.data.content;
      render30Chart("getUserFollowTrendLast30Days", "关注人数", statistic.value.getUserFollowTrendLast30Days);
    } else {
      notification.error({ description: response.data.message });
    }
  } catch (err) {
    loading.value = false;
    notification.error({ description: "数据请求失败" });
  }
};

//--------列表查询-----------
const pagination = ref({
  total : 0,
  current : 1,
  pageSize : 1
});

const getFollowingListByUserId =ref();
getFollowingListByUserId.value = [];

const handleQuery = (param) => {
  if (!param) {
    param = {
      page: 1,
      size: pagination.value.pageSize
    };
  }
  loading.value = true;
  getFollowingListByUserId.value = [];
  axios.get(BASE_URL+"/lyw/web/userfollow/query-ByUserIdList", {
    params: { page: param.page, size: param.size }
  }).then((response) => {
    loading.value = false;
    const data = response.data;
    if (data.success) {
      getFollowingListByUserId.value = data.content.page
      pagination.value.current = param.page;
      pagination.value.total = data.content.total;
    } else {
      notification.error({ description: data.message });
    }
  }).catch(err => {
    loading.value = false;
    notification.error({ description: '请求失败' });
  });
};

const handleTableChange = (pagination) => {
  console.log("看看自带的分页参数都有啥：" + pagination);
  handleQuery({
    page: pagination.current,
    size: pagination.pageSize
  });
};


// ----------------- 图表显示 -----------------
const render30Chart = (id, title, data30) => {
  const dom = document.getElementById(id);
  if (!dom) return;

  const dates = data30.map(item => item.date);
  const nums = data30.map(item => item.num);

  const myChart = echarts.init(dom);
  myChart.setOption({
    title: {
      text: title,
      left: 'center',
      textStyle: { fontSize: 16, fontWeight: 'bold' }
    },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: dates, axisLabel: { rotate: 45 } },
    yAxis: { type: 'value' },
    grid: { left: '5%', right: '5%', bottom: '15%', top: '20%' },
    series: [{
      data: nums,
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 3, color: '#ff3b4f' },
      itemStyle: { color: '#ff3b4f' },
      areaStyle: { color: 'rgba(255,59,79,0.12)' }
    }]
  });
};

onMounted(() => {
  fetchStatistic();
});

handleQuery();
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 24px;
}
.card-title {
  font-size: 20px;
  font-weight: bold;
  color: var(--travel-color-text);
  display: inline-flex;
  gap: 8px;
}
.stat-row { margin-bottom: 24px; }
.chart-container {
  background: var(--travel-color-bg-subtle);
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--travel-color-border);
}
.follow-page__panel {
  padding: 26px;
}
</style>

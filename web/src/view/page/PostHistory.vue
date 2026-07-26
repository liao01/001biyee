<template>
  <a-card
      bordered
      :style="{
    borderRadius: '12px',
    boxShadow: '0 2px 12px rgba(0,0,0,0.08)',
    margin: '20px auto',  /* 增加上下间距，并水平居中 */
    maxWidth: '1200px',   /* 限制一个最大宽度，防止在大屏幕上无限拉伸 */
    padding: '0 20px'     /* 增加内部左右缓冲 */
  }"
  >
    <div class="card-header">
      <span class="card-title">  <TeamOutlined style="margin-right:8px"/>笔记管理</span>
    </div>
    <a-table
        :dataSource="UserHistoryList"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        @change="handleTableChange"
        rowKey="followId"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'postTitle'">
          {{ record.postTitle }}
        </template>
        <template v-if="column.dataIndex === 'postContent'">
          {{ record.postContent }}
        </template>
        <template v-if="column.dataIndex === 'postTime'">
          {{ record.postTime }}
        </template>
        <template v-if="column.dataIndex === 'tagTitle'">
          {{ record.tagTitle }}
        </template>
        <template v-if="column.dataIndex === 'action'">
          <a-space>
            <a-popconfirm
                title="确定要删除这篇文章吗？"
                ok-text="是"
                cancel-text="否"
            >
              <a-button type="link" danger @click="del(record.postId)">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup>


import axios from "axios";
import {message, notification} from "ant-design-vue";
import {onMounted, ref} from "vue";
import store from "../../store/index.js";
import { BASE_URL } from "../../utils/baseUrl";

const loading = ref(false);

const columns = [
  { title: '文章标题', dataIndex: 'postTitle' },
  { title: '文章内容', dataIndex: 'postContent' },
  { title: '发布时间', dataIndex: 'postTime' },
  { title: '分类', dataIndex: 'tagTitle',width: 160 },
  {
    title: '操作',
    dataIndex: 'action',
    width: 160
  }
];

//--------列表查询-----------
const pagination = ref({
  total : 0,
  current : 5,
  pageSize : 5
});

const UserHistoryList =ref();

const handleQuery = (param) => {
  if (!param) {
    param = {
      page: 1,
      size: pagination.value.pageSize
    };
  }
  loading.value = true;
  UserHistoryList.value = [];
  axios.get(BASE_URL+"/lyw/web/post/post-User-search", {
    params: { page: param.page, size: param.size }
  }).then((response) => {
    loading.value = false;
    const data = response.data;
    if (data.success) {
      UserHistoryList.value = data.content.page
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

const del = async (postId) => {
  if (!postId) return;
  console.log("删除帖子ID:", postId); // 确认前端拿到postId

  axios.post(BASE_URL+"/lyw/web/post/post-del", { postId })
      .then(response => {
        const data = response.data;
        if (data.success) {
          notification.success({ description: '删除成功' });
          handleQuery({ page: pagination.value.current, size: pagination.value.pageSize });
        } else {
          message.error(data.message)
        }
      })
      .catch(error => {
        console.error("删除请求失败:", error);
        message.error("删除失败");
      });
}


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
  color: #333;
}
.stat-row { margin-bottom: 24px; }
.chart-container {
  background: #fff;
  padding: 16px;
  border-radius: 12px;
  box-shadow: inset 0 0 8px rgba(0,0,0,0.03);
}
</style>

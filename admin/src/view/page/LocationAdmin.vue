<template>
  <div class="page-container">
    <a-card :bordered="false" class="custom-card shadow-sm">
      <template #title>
        <div class="card-header">
          <div class="header-indicator"></div>
          <environment-outlined class="header-icon" />
          <span class="header-text">地点管理中心</span>
        </div>
      </template>

      <div class="search-section">
        <a-space size="middle">
          <a-input
              v-model:value="searchKeyword"
              placeholder="搜索名称、城市或详细地址..."
              allow-clear
              @pressEnter="handleSearch"
              class="custom-search-input"
          >
            <template #prefix><search-outlined style="color: #bfbfbf" /></template>
          </a-input>
          <a-button type="primary" class="gradient-btn" @click="handleSearch">
            <search-outlined /> 搜索
          </a-button>
          <a-button class="reset-btn" @click="resetSearch">
            <reload-outlined /> 重置
          </a-button>
        </a-space>
      </div>

      <a-table
          :columns="columns"
          :data-source="data"
          row-key="id"
          :loading="loading"
          :pagination="pagination"
          size="middle"
          class="beautify-table"
          @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'city'">
            <a-tag color="processing" class="city-tag">{{ record.city }}</a-tag>
          </template>

          <template v-if="column.key === 'longitude' || column.key === 'latitude'">
            <div class="coordinate-cell">
              <span class="coord-prefix">{{ column.key === 'longitude' ? 'E' : 'N' }}</span>
              <span class="number-font">{{ record[column.key] }}</span>
            </div>
          </template>

          <template v-if="column.key === 'images'">
            <a-image-preview-group>
              <div class="image-wrapper">
                <a-image
                    v-for="(img, index) in record.imageUrlList"
                    :key="index"
                    :src="`${BASE_URL}/lyw${img}`"
                    :width="44"
                    :height="44"
                    class="table-img"
                />
              </div>
            </a-image-preview-group>
          </template>

          <template v-if="column.key === 'action'">
            <a-popconfirm
                title="确定要永久删除该地点数据吗？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="handleDelete(record.id)"
            >
              <a-button type="link" danger size="small" class="delete-link">
                <template #icon><delete-outlined /></template>
                删除
              </a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  EnvironmentOutlined,
  DeleteOutlined,
  SearchOutlined,
  ReloadOutlined
} from '@ant-design/icons-vue'
import axios from "axios"
import { BASE_URL } from "../../utils/baseUrl";

const loading = ref(false)
const data = ref([])
const searchKeyword = ref("")

const columns = [
  { title: '地点名称', dataIndex: 'name', fontWeight: 600 },
  { title: '城市', dataIndex: 'city', key: 'city', width: 110 },
  { title: '详细地址', dataIndex: 'formattedAddress', ellipsis: true },
  { title: '经度', dataIndex: 'longitude', key: 'longitude', width: 120 },
  { title: '纬度', dataIndex: 'latitude', key: 'latitude', width: 120 },
  { title: '实景图', key: 'images', width: 200 },
  { title: '操作', key: 'action', width: 90, align: 'center' }
]

const pagination = ref({
  current: 1,
  pageSize: 6,
  total: 0,
  showSizeChanger: true,
  showTotal: total => `共 ${total} 条数据`
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await axios.get(
        BASE_URL+'/lyw/admin/location/searchPage',
        {
          params: {
            page: pagination.value.current,
            size: pagination.value.pageSize,
            keyword: searchKeyword.value
          }
        }
    )
    if (res.data.success) {
      data.value = res.data.content.page
      pagination.value.total = res.data.content.total
    } else {
      message.error(res.data.message || '数据加载失败')
    }
  } catch (e) {
    message.error('无法连接到服务器')
  } finally {
    loading.value = false
  }
}

const handleTableChange = (pager) => {
  pagination.value.current = pager.current
  pagination.value.pageSize = pager.pageSize
  loadData()
}

const handleSearch = () => {
  pagination.value.current = 1
  loadData()
}

const resetSearch = () => {
  searchKeyword.value = ""
  pagination.value.current = 1
  loadData()
}

const handleDelete = async (id) => {
  try {
    const res = await axios.post(BASE_URL+"/lyw/admin/location/del", { id })
    if (res.data.success) {
      message.success("数据已成功删除")
      loadData()
    } else {
      message.error(res.data.message)
    }
  } catch (e) {
    message.error("操作失败")
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
/* 全局容器：使用更高级的背景色 */
.page-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

/* 卡片样式优化 */
.custom-card {
  border-radius: 12px;
  overflow: hidden;
}

.shadow-sm {
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
}

/* 卡片头部装饰 */
.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-indicator {
  width: 4px;
  height: 18px;
  background-color: #1890ff;
  border-radius: 2px;
}

.header-icon {
  color: #1890ff;
  font-size: 18px;
}

.header-text {
  font-weight: 600;
  font-size: 17px;
  color: #1a1a1a;
}

/* 搜索区域 */
.search-section {
  margin-bottom: 24px;
  padding: 16px;
  background-color: #fafafa;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}

.custom-search-input {
  width: 300px;
  border-radius: 6px;
}

.gradient-btn {
  border-radius: 6px;
  box-shadow: 0 2px 4px rgba(24, 144, 255, 0.2);
}

.reset-btn {
  border-radius: 6px;
}

/* 表格深度美化 */
.beautify-table :deep(.ant-table-thead > tr > th) {
  background-color: #f8f9fb;
  font-weight: 600;
  color: #4a4a4a;
}

.beautify-table :deep(.ant-table-row) {
  transition: all 0.2s;
}

.beautify-table :deep(.ant-table-row:hover) {
  background-color: #f0f7ff !important;
}

/* 城市标签 */
.city-tag {
  border-radius: 4px;
  padding: 0 8px;
  font-weight: 500;
}

/* 经纬度专业展示 */
.coordinate-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.coord-prefix {
  font-size: 10px;
  color: #bfbfbf;
  font-weight: bold;
}

.number-font {
  font-family: 'Consolas', 'Monaco', monospace;
  color: #555;
  font-size: 13px;
}

/* 图片展示 */
.image-wrapper {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.table-img {
  border-radius: 6px;
  object-fit: cover;
  border: 2px solid #fff;
  box-shadow: 0 2px 5px rgba(0,0,0,0.08);
  transition: transform 0.3s;
  cursor: pointer;
}

.table-img:hover {
  transform: translateY(-2px) scale(1.1);
}

/* 操作链接 */
.delete-link {
  font-weight: 500;
}

/* 分页器美化 */
:deep(.ant-pagination-item-active) {
  border-radius: 6px;
  border-color: #1890ff;
}
</style>
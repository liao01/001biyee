<template>
  <div class="page-container">
    <a-card :bordered="false" class="custom-card">
      <template #title>
        <div class="card-header">
          <environment-outlined class="header-icon" />
          <span class="header-text">地点管理</span>
        </div>
      </template>

      <a-table
          :columns="columns"
          :data-source="data"
          row-key="id"
          :loading="loading"
          :pagination="false"
          size="middle"
          class="beautify-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'city'">
            <a-tag color="blue">{{ record.city }}</a-tag>
          </template>

          <template v-if="column.key === 'longitude' || column.key === 'latitude'">
            <span class="number-font">{{ record[column.key] }}</span>
          </template>

          <template v-if="column.key === 'images'">
            <a-image-preview-group>
              <div class="image-wrapper">
                <a-image
                    v-for="(img, index) in record.imageUrlList"
                    :key="index"
                    :src="`http://localhost:8080/lyw${img}`"
                    :width="46"
                    :height="46"
                    class="table-img"
                />
              </div>
            </a-image-preview-group>
          </template>

          <template v-if="column.key === 'action'">
            <a-popconfirm
                title="确定要删除该地点吗？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="handleDelete(record.id)"
            >
              <a-button type="link" danger size="small">
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
import { EnvironmentOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import axios from "axios"

const loading = ref(false)
const data = ref([])

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '城市', dataIndex: 'city', key: 'city', width: 100 },
  { title: '详细地址', dataIndex: 'formattedAddress', ellipsis: true },
  { title: '经度', dataIndex: 'longitude', key: 'longitude' },
  { title: '纬度', dataIndex: 'latitude', key: 'latitude' },
  { title: '实景图', key: 'images', width: 220 },
  { title: '操作', key: 'action', width: 100, align: 'center' }
]

const loadData = async () => {
  loading.value = true
  try {
    const res = await axios.get('http://localhost:8080/lyw/admin/location/findLocationAll')
    if (res.data.success) {
      data.value = res.data.content
    } else {
      message.error(res.data.message || '加载失败')
    }
  } catch (e) {
    message.error('网络请求失败')
  } finally {
    loading.value = false
  }
}

const handleDelete = async (id) => {
  try {
    const res = await axios.post("http://localhost:8080/lyw/admin/location/del", { id })
    if (res.data.success) {
      message.success("删除成功")
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
.page-container {
  padding: 24px;
  background-color: #f0f2f5;
  min-height: 100vh;
}

.custom-card {
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-icon {
  color: #1890ff;
  font-size: 20px;
}

.header-text {
  font-weight: 600;
  font-size: 16px;
  color: #333;
}

.number-font {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', monospace;
  color: #666;
}

.image-wrapper {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.table-img {
  border-radius: 4px;
  object-fit: cover;
  border: 1px solid #f0f0f0;
  transition: all 0.3s;
}

.table-img:hover {
  transform: scale(1.05);
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

:deep(.ant-table-thead > tr > th) {
  background-color: #fafafa;
  font-weight: 600;
}

:deep(.ant-table-tbody > tr:nth-child(even)) {
  background-color: #fafafa;
}
</style>
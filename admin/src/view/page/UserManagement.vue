<template>
  <a-table :columns="columns" :data-source="data">
    <template #headerCell="{ column }">
    </template>

    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'name'">
        <a>
          {{ record.name }}
        </a>
      </template>
      <template v-else-if="column.key === 'tags'">
        <span>
          <a-tag
              v-for="tag in record.tags"
              :key="tag"
              :color="tag === 'loser' ? 'volcano' : tag.length > 5 ? 'geekblue' : 'green'"
          >
            {{ tag.toUpperCase() }}
          </a-tag>
        </span>
      </template>
      <template v-else-if="column.key === 'action'">
        <span>
          <a>Invite 一 {{ record.name }}</a>
          <a-divider type="vertical" />
          <a>Delete</a>
          <a-divider type="vertical" />
          <a class="ant-dropdown-link">
            More actions
            <down-outlined />
          </a>
        </span>
      </template>
    </template>
  </a-table>
</template>
<script setup>
const columns = [
  {
    name: '用户名',
    dataIndex: 'name',
    key: 'name',
  },
  {
    title: '操作',
    dataIndex: 'age',
    key: 'age',
  }
];
const data = [
  {
    key: '1',
    name: 'John Brown',
    age: 32
  },
  {
    key: '2',
    name: 'Jim Green',
    age: 42
  },
  {
    key: '3',
    name: 'Joe Black',
    age: 32
  },
];
</script>
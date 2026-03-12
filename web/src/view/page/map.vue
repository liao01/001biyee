<template>
  <div class="map-container">
    <div id="mapDiv" class="full-map"></div>

    <div class="sidebar">
      <!-- 搜索框 -->
      <a-input-search
          v-model:value="searchQuery"
          placeholder="搜索景区 / 城市 / 地址"
          allow-clear
          enter-button
          @search="handleSearch"
          @clear="resetSearch"
          class="search-box"
      />
      <div class="location-list" v-if="filteredLocations.length > 0">
        <div
            v-for="item in filteredLocations"
            :key="item.id"
            class="list-item"
            :class="{ active: activeLocation?.id === item.id }"
            @click="focusLocation(item)"
        >
          <div class="item-info">
            <h4>{{ item.name }}</h4>
            <p>{{ item.formattedAddress || '查看详情' }}</p>
          </div>
          <right-outlined />
        </div>
      </div>
      <a-empty v-else :image="simpleImage" description="暂无景区数据" />
    </div>

    <transition name="slide-fade">
      <LocationCard
          v-if="activeLocation"
          :location="activeLocation"
          @close="handleClose"
          class="location-card-overlay"
      />
    </transition>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from "vue";
import axios from "axios";
import { message, Empty } from "ant-design-vue";
import { RightOutlined } from '@ant-design/icons-vue';
import LocationCard from "../../components/card/locationCard.vue";

const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE;
let map = null;
const markers = []; // 存储 marker 引用

const locations = ref([]);
const activeLocation = ref(null);
const searchQuery = ref("");
const previousView = ref(null);
const allLocations = ref([]); // 保存全部地点

// 过滤后的列表
const filteredLocations = computed(() => locations.value);

onMounted(() => {
  loadLocations();
});

function loadLocations() {
  axios.get("http://localhost:8080/lyw/web/location/findLocationAll")
      .then(res => {
        if (res.data.success) {
          locations.value = res.data.content || [];
          allLocations.value = res.data.content || [];
          initMap();
        }
      })
      .catch(() => message.error("获取数据失败"));
}

function handleSearch(value) {
  const keyword = value?.trim();
  if (!keyword) {
    resetSearch();
    return;
  }

  axios.get("http://localhost:8080/lyw/web/location/searchLocation", {
    params: { keyword }
  }).then(res => {
    if (res.data.success) {
      locations.value = res.data.content || [];
      activeLocation.value = null;
      previousView.value = null;

      // 重新渲染地图 Marker
      renderMarkers();

      if (locations.value.length === 0) {
        message.info("未搜索到相关景区");
      }
    }
  }).catch(() => {
    message.error("搜索失败");
  });
}

function resetSearch() {
  searchQuery.value = "";
  locations.value = allLocations.value;
  activeLocation.value = null;
  previousView.value = null;
  renderMarkers();
}

function initMap() {
  map = new AMap.Map("mapDiv", {
    zoom: 5,
    center: [108.948024, 34.223159], // 默认中国中心
    viewMode: "3D",
    pitch: 45, // 倾斜角度，更有立体感
    mapStyle: "amap://styles/whitesmoke", // 换一个清新亮丽的风格
  });

  renderMarkers();
}

function renderMarkers() {
  // 清除旧的 markers
  markers.forEach(m => m.setMap(null));

  locations.value.forEach(loc => {
    if (!loc.longitude || !loc.latitude) return;

    // 自定义 Marker 内容 (HTML)
    const markerContent = `
      <div class="custom-marker">
        <div class="marker-pin"></div>
        <div class="marker-label">${loc.name}</div>
      </div>
    `;

    const marker = new AMap.Marker({
      position: [loc.longitude, loc.latitude],
      content: markerContent,
      offset: new AMap.Pixel(-15, -15),
      extData: loc.id
    });

    marker.on("click", () => focusLocation(loc));
    marker.setMap(map);
    markers.push(marker);
  });
}

// 2. 修改聚焦函数
function focusLocation(loc) {
  // 如果当前没有激活的景区，说明是从全局视角跳转的，记录下当前位置
  if (!activeLocation.value) {
    previousView.value = {
      zoom: map.getZoom(),
      center: map.getCenter()
    };
  }

  activeLocation.value = loc;
  // 执行飞行效果
  map.setZoomAndCenter(15, [loc.longitude, loc.latitude], false, 1000);
}

// 3. 新增关闭弹窗的处理函数
function handleClose() {
  activeLocation.value = null;

  // 如果有记录之前的视角，则跳回
  if (previousView.value) {
    map.setZoomAndCenter(
        previousView.value.zoom,
        [previousView.value.center.lng, previousView.value.center.lat],
        false,
        1000
    );
    // 重置记录，防止逻辑混乱
    previousView.value = null;
  } else {
    // 如果没有记录（异常情况），回到默认中心
    map.setZoomAndCenter(5, [108.948024, 34.223159], false, 1000);
  }
}
</script>

<style scoped>
/* 容器全屏化 */
.map-container {
  position: relative;
  width: 100%;
  height: calc(100vh - 64px); /* 减去你的导航栏高度 */
  overflow: hidden;
  background: #f0f205;
}

.full-map {
  width: 100%;
  height: 100%;
}

/* 左侧侧边栏 - 玻璃拟态 */
.sidebar {
  position: absolute;
  left: 20px;
  top: 20px;
  bottom: 20px;
  width: 320px;
  z-index: 100;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  padding: 20px;
}

.search-box {
  margin-bottom: 20px;
}

.location-list {
  flex: 1;
  overflow-y: auto;
}

/* 列表条目美化 */
.list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
  margin-bottom: 8px;
  border: 1px solid transparent;
}

.list-item:hover {
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
}

.list-item.active {
  background: #1890ff;
  color: white;
}

.item-info h4 {
  margin: 0;
  font-size: 15px;
  color: inherit;
}
.item-info p {
  margin: 4px 0 0;
  font-size: 12px;
  opacity: 0.8;
}

/* 详情卡片悬浮 */
.location-card-overlay {
  position: absolute;
  right: 20px;
  bottom: 40px; /* 放在右下角，避免遮挡视觉中心 */
  width: 400px;
  z-index: 999;
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.15);
  border-radius: 20px;
}

/* 自定义 Marker 样式 */
:deep(.custom-marker) {
  display: flex;
  flex-direction: column;
  align-items: center;
}

:deep(.marker-pin) {
  width: 24px;
  height: 24px;
  background: #1890ff;
  border: 3px solid #fff;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
  box-shadow: 0 2px 5px rgba(0,0,0,0.3);
}

:deep(.marker-label) {
  background: white;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  margin-top: 5px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  white-space: nowrap;
}

</style>
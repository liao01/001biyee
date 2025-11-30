<template>
  <div id="mapDiv" style="width:100%; height:100%;"></div>
  <!-- 卡片浮层 -->
  <LocationCard
      v-if="activeLocation"
      :location="activeLocation"
      @close="activeLocation = null"
      class="location-card"
  />
</template>

<script setup>
import axios from "axios";
import {message} from "ant-design-vue";
import {ref} from "vue";
import LocationCard from "../../components/card/locationCard.vue";

let map;
const locations = ref([]);
const activeLocation = ref(null);

// 请求后端获取所有景点数据
axios.get("http://localhost:8080/lyw/web/location/findLocationAll")
    .then(response => {
      const data = response.data;
      if (data.success) {
        locations.value = data.content;
        message.success("返回成功!");
        console.log(locations.value);

        // 数据返回后初始化地图
        initMap();
      } else {
        message.error(data.message)
      }
    })
    .catch(err => {
      message.error("请求失败：" + err)
    });

// 初始化地图函数
function initMap() {
  map = new AMap.Map('mapDiv', {
    zoom: 5,
    center: [116.397428, 39.90923], // 默认中心点，北京天安门
    viewMode: '2D',
    mapStyle: 'amap://styles/macaron',
    resizeEnable: true
  });

  AMap.plugin(['AMap.ToolBar', 'AMap.Scale'], function () {
    map.addControl(new AMap.ToolBar());
    map.addControl(new AMap.Scale());
  });

  // 遍历 locations 数组添加 Marker
  locations.value.forEach(loc => {
    const marker = new AMap.Marker({
      position: [loc.longitude, loc.latitude],
      title: loc.formattedAddress || loc.address,
      map: map
    });

    marker.on('click', function () {
      activeLocation.value = loc; // 点击 Marker → 设置当前选中景点
      map.setZoomAndCenter(12, marker.getPosition()); // 地图中心移动并放大
    });
  });
}
</script>

<style scoped>
.location-card {
  position: absolute;
  right: 30px;
  top: 150px;
  width: 500px;
  z-index: 999;
}
</style>

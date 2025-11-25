<template>
  <div id="mapDiv" style="width:100%; height:100%;"></div>
</template>

<script setup>
import axios from "axios";
import {message} from "ant-design-vue";
import {ref} from "vue";

let map;
const locations = ref([]);

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

    const infoWindow = new AMap.InfoWindow({
      content: `
        <div style="padding:10px;">
          <h4>${loc.formattedAddress}</h4>
          <p>${loc.city}, ${loc.district}</p>
        </div>
      `,
      offset: new AMap.Pixel(0, -30)
    });

    marker.on('click', function () {
      infoWindow.open(map, marker.getPosition());
      map.setZoomAndCenter(12, marker.getPosition());
    });
  });
}
</script>

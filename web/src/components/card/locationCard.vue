<template>
  <div class="info-box-container">
    <header class="header">
      <h4 class="title">{{ location.name }}</h4>
      <button class="close-btn" @click="$emit('close')" title="关闭">
        <span class="icon-close">×</span>
      </button>
    </header>

    <div class="content">
      <div class="address-section">
        <div class="tag-row">
          <span class="location-tag">{{ location.province }}</span>
          <span class="location-tag">{{ location.city }}</span>
        </div>
        <p class="full-address">
          <i class="icon-map">📍</i> {{ location.formattedAddress || `${location.district} ${location.description}` }}
        </p>
      </div>

      <div class="image-gallery" v-if="location.imageUrlList?.length">
        <div class="image-wrapper">
          <img
              v-for="(url, index) in location.imageUrlList"
              :key="index"
              :src="fullImageUrl(url)"
              :alt="location.name"
              class="gallery-img"
              loading="lazy"
              @error="handleImageError"
          />
        </div>
      </div>

      <div class="description-section">
        <p class="desc-text">{{ location.description }}</p>
      </div>
    </div>

    <footer class="footer">
      <button class="action-btn primary" @click="handleNavigate">到这去</button>
    </footer>
  </div>
</template>

<script setup>
const props = defineProps({
  location: {
    type: Object,
    required: true,
    default: () => ({})
  }
});

// 建议：基地址应从环境变量或全局配置中获取，避免硬编码
const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/lyw';

const fullImageUrl = (url) => {
  if (!url) return 'https://via.placeholder.com/140x100?text=No+Image';
  return url.startsWith('http') ? url : `${API_BASE_URL}${url}`;
};

const handleImageError = (e) => {
  e.target.src = 'https://via.placeholder.com/140x100?text=Image+Error';
};

const handleNavigate = () => {
  const { lng, longitude, lat, latitude, name } = props.location;

  // 兼容不同字段名：优先取 longitude/latitude
  const targetLng = longitude || lng;
  const targetLat = latitude || lat;

  if (!targetLng || !targetLat) {
    return alert("该地点缺少经纬度信息");
  }

  // 1. 尝试动态获取用户位置
  if (navigator.geolocation) {
    // 可以在界面上增加一个 Loading 状态
    navigator.geolocation.getCurrentPosition(
        (position) => {
          // 成功：获取到实时动态坐标
          const curLng = position.coords.longitude;
          const curLat = position.coords.latitude;

          // 跳转高德：带上动态起点 from 和 终点 to
          // coordinate=gcj02 表示使用的是高德/腾讯坐标系
          const url = `https://uri.amap.com/navigation?from=${curLng},${curLat},我的位置&to=${targetLng},${targetLat},${name}&mode=car&policy=1&src=mypage&coordinate=gcj02&callnative=1`;
          window.open(url, '_blank');
        },
        (error) => {
          // 失败：用户拒绝、非HTTPS环境或定位超时
          console.warn("实时定位失败，切换为粗略定位:", error.message);
          fallbackNavigate(targetLng, targetLat, name);
        },
        {
          enableHighAccuracy: true, // 高精度
          timeout: 5000             // 5秒超时
        }
    );
  } else {
    fallbackNavigate(targetLng, targetLat, name);
  }
};

// 降级方案：不传 from，由高德地图网页版根据 IP 自动识别起点
const fallbackNavigate = (lng, lat, name) => {
  const url = `https://uri.amap.com/navigation?to=${lng},${lat},${name}&mode=car&policy=1&src=mypage&coordinate=gaode&callnative=1`;
  window.open(url, '_blank');
};
</script>

<style scoped>
.info-box-container {
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  width: 360px; /* 调整了宽度，更适合作为侧边弹窗 */
  max-height: 520px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  border: 1px solid #eee;
}

/* 顶部样式 */
.header {
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #f0f0f0;
}

.title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.close-btn {
  border: none;
  background: #f5f5f5;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}

.close-btn:hover {
  background: #e8e8e8;
}

/* 内容区域 */
.content {
  padding: 16px;
  overflow-y: auto;
  flex: 1;
}

/* 地址标签 */
.tag-row {
  margin-bottom: 8px;
}

.location-tag {
  background: #e6f7ff;
  color: #1890ff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  margin-right: 6px;
}

.full-address {
  font-size: 13px;
  color: #666;
  margin: 8px 0;
  line-height: 1.4;
}

/* 相册样式 */
.image-gallery {
  margin: 16px 0;
}

.image-wrapper {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 8px;
}

/* 美化滚动条 */
.image-wrapper::-webkit-scrollbar {
  height: 6px;
}
.image-wrapper::-webkit-scrollbar-thumb {
  background: #ddd;
  border-radius: 10px;
}

.gallery-img {
  width: 160px;
  height: 110px;
  object-fit: cover;
  border-radius: 8px;
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(0,0,0,0.1);
  transition: transform 0.2s;
}

.gallery-img:hover {
  transform: scale(1.02);
}

/* 描述 */
.desc-text {
  font-size: 14px;
  color: #444;
  line-height: 1.6;
  white-space: pre-line; /* 保留后端换行符 */
}

/* 底部按钮 */
.footer {
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
}

.action-btn {
  width: 100%;
  padding: 10px;
  border-radius: 8px;
  border: none;
  font-weight: 500;
  cursor: pointer;
  background: #1890ff;
  color: white;
  transition: opacity 0.2s;
}

.action-btn:hover {
  opacity: 0.9;
}
</style>
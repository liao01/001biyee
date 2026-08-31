<template>
  <nav :class="['travel-sider', { 'travel-sider--mobile': mobile }]" aria-label="主导航">
    <div class="travel-sider__primary">
      <button
        v-for="item in visibleItems"
        :key="item.path"
        :class="['travel-sider__item', { 'is-active': selectedKeys.includes(item.path) }]"
        type="button"
        @click="handleMenuClick(item.path, item.needLogin)"
      >
        <component :is="item.icon" class="travel-sider__icon" />
        <span>{{ item.label }}</span>
      </button>
    </div>

    <div v-if="!mobile" class="travel-sider__footer">
      <p>分享旅行 · 遇见世界</p>
      <the-login ref="loginRef" />
    </div>
  </nav>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import {
  HomeOutlined, PlusSquareOutlined, RobotOutlined,
  CompassOutlined, UserOutlined, HistoryOutlined, StarOutlined, BarChartOutlined
} from '@ant-design/icons-vue'
import TheLogin from "./the-login.vue"
import { useRouter } from "vue-router"
import { useStore } from "vuex"

const props = defineProps({
  mobile: {
    type: Boolean,
    default: false,
  },
})

const selectedKeys = ref(['/CardList'])
const store = useStore()
const router = useRouter()

const navigationItems = [
  { path: '/CardList', label: '发现', icon: HomeOutlined, needLogin: false, mobile: true },
  { path: '/uploadPost', label: '发布', icon: PlusSquareOutlined, needLogin: true, mobile: true },
  { path: '/Map', label: '地图', icon: CompassOutlined, needLogin: true, mobile: true },
  { path: '/AI2', label: '旅游助手', icon: RobotOutlined, needLogin: true, mobile: true },
  { path: '/UserProfile', label: '我的', icon: UserOutlined, needLogin: true, mobile: true },
  { path: '/Userfollow', label: '粉丝数据', icon: BarChartOutlined, needLogin: true },
  { path: '/PostHistory', label: '发布历史', icon: HistoryOutlined, needLogin: true },
  { path: '/CardlistView', label: '浏览历史', icon: HistoryOutlined, needLogin: true },
  { path: '/FavoriteList', label: '我的收藏', icon: StarOutlined, needLogin: true },
]

const visibleItems = computed(() => props.mobile
  ? navigationItems.filter((item) => item.mobile)
  : navigationItems)

const handleMenuClick = (path, needLogin = false) => {
  if (needLogin && !store.state.member.id) {
    if (window.showLogin) window.showLogin()
    return
  }
  router.push(path)
}

watch(() => router.currentRoute.value.path, (newPath) => {
  selectedKeys.value = [newPath]
}, { immediate: true })
</script>

<style scoped>
.travel-sider {
  background: var(--travel-color-bg);
  display: flex;
  flex-direction: column;
  height: 100%;
  justify-content: space-between;
  padding: 18px 14px 24px;
}

.travel-sider__primary {
  display: grid;
  gap: 4px;
}

.travel-sider__item {
  align-items: center;
  background: transparent;
  border: 0;
  border-radius: 10px;
  color: var(--travel-color-text-secondary);
  cursor: pointer;
  display: flex;
  font: inherit;
  font-size: 14px;
  font-weight: 560;
  gap: 13px;
  min-height: 44px;
  padding: 0 14px;
  text-align: left;
  transition: background var(--travel-transition), color var(--travel-transition);
  width: 100%;
}

.travel-sider__item:hover {
  background: var(--travel-color-bg-subtle);
  color: var(--travel-color-text);
}

.travel-sider__item.is-active {
  background: var(--travel-color-brand-soft);
  color: var(--travel-color-brand);
  font-weight: 680;
}

.travel-sider__icon {
  font-size: 18px;
}

.travel-sider__footer {
  border-top: 1px solid var(--travel-color-border);
  color: var(--travel-color-text-muted);
  font-size: 12px;
  line-height: 1.6;
  padding: 18px 10px 0;
}

.travel-sider--mobile {
  border-top: 1px solid var(--travel-color-border);
  box-shadow: 0 -8px 24px rgb(24 30 40 / 8%);
  display: block;
  height: 68px;
  padding: 5px 8px env(safe-area-inset-bottom);
}

.travel-sider--mobile .travel-sider__primary {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
}

.travel-sider--mobile .travel-sider__item {
  flex-direction: column;
  font-size: 10px;
  gap: 2px;
  justify-content: center;
  min-height: 56px;
  padding: 4px;
}

.travel-sider--mobile .travel-sider__item.is-active {
  background: transparent;
}
</style>

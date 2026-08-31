import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import Antd from 'ant-design-vue';
import 'ant-design-vue/dist/reset.css';
import * as Icons from '@ant-design/icons-vue';
import router from "./router";
import axios from "axios";
import store from "./store/index.js";
import { createPinia } from 'pinia'
import { createPostDetailNavigation, postDetailNavigationKey } from './modules/post-detail/postDetailNavigation.js'
import { configureGlobalAxios } from './utils/baseUrl.js'
import request from './utils/request.js'
import { identitySession } from './modules/identity/identityClient.js'

const app = createApp(App);
const pinia = createPinia()
app.provide(postDetailNavigationKey, createPostDetailNavigation(router))
app.use(pinia).use(Antd).use(store).use(router)

// 全局使用图标
const icons = Icons;
for (const i in icons) {
    app.component(i, icons[i]);
}

configureGlobalAxios(axios)
identitySession.install(axios)
identitySession.install(request)
// 先恢复身份再挂载业务页面，避免首屏受保护请求与 Cookie 刷新竞争。
identitySession.restore().finally(() => app.mount('#app'))

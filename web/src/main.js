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

const app = createApp(App);
const pinia = createPinia()
app.provide(postDetailNavigationKey, createPostDetailNavigation(router))
app.use(pinia).use(Antd).use(store).use(router)

// 全局使用图标
const icons = Icons;
for (const i in icons) {
    app.component(i, icons[i]);
}

app.mount('#app');

//axios拦截器
axios.interceptors.request.use(function (config) {
    console.log('请求参数：', config);
    let _token = store.state.member.token;
    if (_token){
        config.headers.token = _token;
        console.log("请求headers增加token:",_token);
    }
    return config;
}, error => {
    return Promise.reject(error);
});
axios.interceptors.response.use(function (response) {
    console.log('返回结果：', response);
    return response;
}, error => {
        console.log("未登录");
    return Promise.reject(error);
});

configureGlobalAxios(axios);

import axios from "axios";

// 创建 axios 实例
const request = axios.create({
    baseURL: import.meta.env.VITE_BASE_URL,
    timeout: 5000
});

export default request;
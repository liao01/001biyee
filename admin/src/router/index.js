import { createRouter, createWebHistory } from 'vue-router'

import Home from '../view/home.vue'
import Login from '../view/login.vue'
import Dashboard from '../view/page/Dashboard.vue'

const routes = [
  {
    path: '/home',
    component: Home,
    redirect: '/home/dashboard',
    children: [
      {
        path: 'dashboard',   // ✅ 不加 /
        component: Dashboard
      }
    ]
  },
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    component: Login
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router

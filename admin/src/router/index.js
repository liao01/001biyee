import { createRouter, createWebHistory } from 'vue-router'

import Home from '../view/home.vue'
import Login from '../view/login.vue'
import Dashboard from '../view/page/Dashboard.vue'
import UserManagement from "../view/page/UserManagement.vue";

const routes = [
  {
    path: '/home',
    component: Home,
    redirect: '/home/dashboard',
    children: [
      {
        path: 'dashboard',
        component: Dashboard
      },{
        path: 'UserManagement',
        component: UserManagement
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

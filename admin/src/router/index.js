import { createRouter, createWebHistory } from 'vue-router'

import Home from '../view/home.vue'
import Login from '../view/login.vue'
import Dashboard from '../view/page/Dashboard.vue'
import UserManagement from "../view/page/UserManagement.vue";
import uploadMap from "../view/page/upload-map.vue";
import LocationAdmin from "../view/page/LocationAdmin.vue";

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
      },{
        path: 'uploadMap',
        component: uploadMap
      },{
        path: 'LocationAdmin',
        component: LocationAdmin
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
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

export default router

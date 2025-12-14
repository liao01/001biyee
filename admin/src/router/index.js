import {createRouter, createWebHistory} from 'vue-router'
import Home from "../view/home.vue"
import Login from "../view/login.vue";
import Register from "../view/register.vue";
import Forgot from "../view/forgot.vue";



const routes = [{
  path :"/home",
  component:Home,
  children:[
  ]
},{
  path :"/",
  redirect:"/login"
},{
  path :"/login",
  component:Login
},{
  path :"/register",
  component:Register
},{
  path :"/forgot-password",
  component:Forgot
}
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router

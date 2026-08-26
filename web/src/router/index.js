import {createRouter, createWebHistory} from 'vue-router'
import Home from "../view/home.vue"
import CardList from "../view/page/cardlist.vue"
import uploadPost from "../view/page/upload-post.vue"
import Userfollow from "../view/page/Userfollow.vue";
import AI from "../view/page/ai.vue"
import AI2 from "../view/page/ai2.vue"
import PostHistory from "../view/page/PostHistory.vue";
import Map from "../view/page/map.vue";
import CardlistView from "../view/page/cardlistView.vue";
import FavoriteList from "../view/page/FavoriteList.vue";
import UserDetail from "../view/page/UserDetail.vue";
import UserProfile from "../view/page/UserProfile.vue";
import AuthorDetail from "../view/page/AuthorDetail.vue"


const routes = [{
  path:"/",
  component:Home,
  redirect: "/CardList",
  children:[
    {
      path:"CardList",
      component:CardList
    },{
      path:"uploadPost",
      component:uploadPost
    },{
      path:"Userfollow",
      component:Userfollow
    },{
      path:"ai",
      component:AI
    },{
      path:"ai2",
      component:AI2
    },{
      path:"PostHistory",
      component:PostHistory
    },{
      path:"Map",
      component:Map
    },{
      path:"CardlistView",
      component:CardlistView
    },{
      path:"FavoriteList",
      component:FavoriteList
    },{
      path:"UserDetail",
      component:UserDetail
    },{
      path:"UserProfile",
      component:UserProfile
    },{
      path: "AuthorDetail/:authorId",
      name: "author-detail",
      component: AuthorDetail
    }
  ]

}]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router

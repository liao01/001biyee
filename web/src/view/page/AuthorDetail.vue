<template>
  <div class="travel-page author-page">
    <ProfileHeader :profile="profileData" :show-actions="false" />

    <section class="author-page__content travel-panel">
      <div class="travel-tabs author-page__tabs">
        <button
            :class="['travel-tab', { 'is-active': activeTab === 'note' }]"
            @click="switchTab('note')">
          旅行发布
        </button>

        <button
            :class="['travel-tab', { 'is-active': activeTab === 'favorite' }]"
            @click="switchTab('favorite')">
          收藏
        </button>
      </div>

      <div v-if="!cardList.length" class="travel-empty">这位旅行者还没有公开内容。</div>
      <div v-else class="waterfall-wrapper">
        <Waterfall
            :list="cardList"
            :width="280"
            :gutter="16"
            :has-around-gutter="true"
            background-color="transparent"
        >
          <template #item="{ item }">
            <a-card hoverable class="note-card" @click="showCardModal(item)">
              <template #cover>
                <img :src="baseUrl + item.raw.imageUrls?.split(',')[0]" :alt="item.title">
              </template>
              <a-card-meta :title="item.title">
                <template #description>{{ item.description }}</template>
              </a-card-meta>
            </a-card>
          </template>
        </Waterfall>
      </div>
    </section>

    <PostDetail v-model:open="detailOpen" :post-id="selectedPostId" />
  </div>
</template>


<script setup>
import { computed, ref, watch } from 'vue'
import {message, notification} from 'ant-design-vue'
import axios from 'axios'
import { useRoute } from 'vue-router'
import {Waterfall} from "vue-waterfall-plugin-next";
import PostDetail from "../../modules/post-detail/PostDetail.vue";
import ProfileHeader from '../../components/travel/ProfileHeader.vue'
import { BASE_URL } from "../../utils/baseUrl";

const baseUrl = BASE_URL+'/lyw'
const defaultAvatar = ''
const route = useRoute()
// 用户数据
const user = ref({})
const userId = computed(() => String(route.params.authorId || ''))
const cardList = ref([])
const selectedPostId = ref(null)
const detailOpen = ref(false)
const likecount = ref(0)
const following = ref({})
const activeTab = ref('note')

const MAX_LENGTH = 10
const statistic = ref({});
const profileData = computed(() => ({
  name: user.value.username || user.value.name || '旅行者',
  bio: user.value.bio,
  location: user.value.location || 'IP 属地未填写',
  avatar: user.value.avatar ? baseUrl + user.value.avatar : '',
  stats: [
    { label: '关注', value: following.value || 0 },
    { label: '粉丝', value: statistic.value.countFollowers || 0 },
    { label: '获赞', value: likecount.value || 0 },
  ],
}))

const fetchStatistic = async () => {
  try {
    const response = await axios.get(BASE_URL+"/lyw/web/userfollow/query-statistic");
    if (response.data.success) {
      statistic.value = response.data.content;
    } else {
      notification.error({ description: response.data.message });
    }
  } catch (err) {
    notification.error({ description: "数据请求失败" });
  }
};

const switchTab = (tab) => {
  if (activeTab.value === tab) return; // 已选中，不重复请求
  activeTab.value = tab;

  if (tab === 'note') {
    fetchUserPosts();
  } else if (tab === 'favorite') {
    fetchUserFavorites();
  }
};

const fetchUserPosts = async () => {
  try {
    const response = await axios.post(BASE_URL+"/lyw/web/post/post-UserPostQuery", {
      userid: userId.value
    });
    const data = response.data;
    if (data.success) {
      cardList.value = (data.content || []).map(post => ({
        raw: post,
        title: post.postTitle,
        description: post.postContent.length > MAX_LENGTH ? post.postContent.substring(0, MAX_LENGTH) + '...' : post.postContent,
        membername: post.postMembername,
        postTime: post.postTime
      }));
    } else {
      message.error(data.message);
    }
  } catch (err) {
    console.error(err);
    message.error('请求失败');
  }
};

const fetchUserFavorites = async () => {
  try {
    const response = await axios.post(BASE_URL+"/lyw/web/post/post-list-Favorite-Posts", {
      userid: userId.value
    });
    const data = response.data;
    if (data.success) {
      cardList.value = (data.content || []).map(post => ({
        raw: post,
        title: post.postTitle,
        description: post.postContent.length > MAX_LENGTH ? post.postContent.substring(0, MAX_LENGTH) + '...' : post.postContent,
        membername: post.postMembername,
        postTime: post.postTime
      }));
    } else {
      message.error(data.message);
    }
  } catch (err) {
    console.error(err);
    message.error('请求失败');
  }
};

const loadAuthorPage = () => {
  if (!userId.value) return
  axios.get(BASE_URL+"/lyw/web/UserProFile/findAllUser", {
    params: {
      userId: userId.value
    }
  }).then(response => {
    const data = response.data
    if (data.success) {
      user.value =  data.content[0]
    } else {
      message.error(data.message)
    }
  }).catch(err => {
    console.error(err)
    message.error('请求失败')
  })

  axios.get(BASE_URL+"/lyw/web/userAction/User-Like-Count", {
    params: { userId: userId.value }
  }).then(response => {
    const data = response.data;
    if (data.success) {
      // content 是数字，直接赋值
      likecount.value = data.content;
      console.log("用户获赞数:", likecount.value);
    } else {
      message.error(data.message);
    }
  }).catch(err => {
    console.error(err);
    message.error('请求失败');
  })

  axios.get(BASE_URL+"/lyw/web/userfollow/byUserIds", {
    params: { userId: userId.value }
  }).then(response => {
    const data = response.data;
    if (data.success) {
      // content 是数字，直接赋值
      following.value = data.content;
      console.log("用户关注数:", following.value);
    } else {
      message.error(data.message);
    }
  }).catch(err => {
    console.error(err);
    message.error('请求失败');
  })

  fetchUserPosts();
  fetchStatistic();
}

watch(() => route.params.authorId, loadAuthorPage, { immediate: true })

// 格式化性别
const genderText = (val) => {
  switch (val) {
    case 1:
      return '男'
    case 2:
      return '女'
    default:
      return '未知'
  }
}

// 格式化生日
const formatDate = (isoDate) => {
  if (!isoDate) return '未填写'
  const date = new Date(isoDate)
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}

const showCardModal = (item) => {
  selectedPostId.value = item.raw.postId
  detailOpen.value = true
}
</script>

<style scoped>
.author-page {
  display: grid;
  gap: 22px;
}

.author-page__content {
  overflow: hidden;
}

.author-page__tabs {
  padding: 0 26px;
}

.waterfall-wrapper {
  padding: 24px 24px 40px;
}

.note-card {
  border: 1px solid var(--travel-color-border) !important;
  border-radius: 12px !important;
  overflow: hidden;
  box-shadow: none !important;
  transition: transform var(--travel-transition), box-shadow var(--travel-transition) !important;
}

.note-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 28px rgb(24 30 40 / 8%) !important;
}

:deep(.ant-card-cover img) {
  border-radius: 12px 12px 0 0;
}

</style>

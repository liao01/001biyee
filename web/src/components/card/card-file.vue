<template>
  <a-modal
      v-model:open="open"
      width="1500px"
      @ok="handleOk"
      :footer="null"
  >
    <template #title>
      <span style="font-size:28px; font-weight:bold">{{ currentCard?.title }}</span>
    </template>
    <a-row gutter="16" align="top">
      <!-- 左边轮播图 -->
      <a-col :span="12">
        <div class="carousel-container" v-if="currentCard?.images?.length">
          <div
              class="carousel-track"
              :style="{ transform: `translateX(-${currentIndex * 100}%)` }"
          >
            <div v-for="(img, idx) in currentCard.images" :key="idx" class="carousel-item">
              <img :src="baseUrl + img" alt="" />
            </div>
          </div>

          <button v-if="currentCard.images.length > 1" class="carousel-prev" @click.stop="prev">&#10094;</button>
          <button v-if="currentCard.images.length > 1" class="carousel-next" @click.stop="next">&#10095;</button>

          <div v-if="currentCard.images.length > 1" class="carousel-dots">
            <span
                v-for="(img, idx) in currentCard.images"
                :key="idx"
                :class="{ active: idx === currentIndex }"
                @click="goTo(idx)"
            ></span>
          </div>
        </div>
      </a-col>

      <!-- 右边文字 + 用户信息 + 评论区 -->
      <a-col :span="12">
        <div class="right-content">
          <!-- 用户信息 -->
          <div class="user-info">
            <div class="user-left">
                <a-avatar size="48" :src="baseUrl + currentCard?.avatar"
                          style="cursor: pointer"
                          @click = "goAuthorPage" />
              <div class="user-text">
                <span class="username">{{ currentCard?.membername }}</span>
                <span class="user-id" v-if="currentCard?.userId === member.value?.id">(自己)</span>
              </div>
            </div>
            <a-button
                :type="isFollowed ? 'default' : 'primary'"
                size="small"
                @click="handleFollow"
                v-if="currentCard.userId !== member.value?.id"
            >
              {{ isFollowed ? '已关注' : '关注' }}
            </a-button>
          </div>

          <!-- 帖子内容 -->
          <div class="post-content">
            <p style="font-size: 25px">{{ currentCard?.description }}</p>
            <span class="post-time">{{ currentCard?.postTime ? dayjs(currentCard.postTime).format('YYYY-MM-DD') : '' }}</span>
          </div>

          <a-divider dashed />

          <div class="comments-section">
            <p class="comments-title">评论</p>
            <a-list bordered split size="small">
              <a-list-item v-for="(comment, idx) in commentList" :key="idx" style="padding: 8px 0;">
                <a-row gutter="[8, 4]">
                  <!-- 用户名和评论内容 -->
                  <a-col :span="20">
                    <div>
                      <a-avatar size="48" :src="baseUrl +comment.avatar" />

                      <span class="comment-user">{{ comment.username }}：</span>
                      <!-- 编辑状态 -->
                      <a-textarea
                          v-if="comment.editing"
                          v-model:value="comment.editContent"
                          size="small"
                          @pressEnter="saveComment(comment, idx)"
                          style="width: 100%;height: auto"
                      />
                      <!-- 普通显示状态 -->
                      <span v-else class="comment-text">{{ comment.content }}</span>
                    </div>
                  </a-col>

                  <!-- 评论时间 + 操作按钮 -->
                  <a-col :span="4" style="text-align:right">
                    <div class="comment-time">{{ dayjs(comment.time).format('YYYY-MM-DD') }}</div>
                    <br>
                    <a-button
                        type="link"
                        @click="deleteComment(comment, idx)"
                        v-if="comment.username == member.name"
                    >删除</a-button>
                    <a-button
                        type="link"
                        v-if="comment.username == member.name"
                        @click="updateComment(comment, idx)"
                    >{{ comment.editing ? '取消' : '编辑' }}</a-button>
                    <a-button
                        type="link"
                        v-if="comment.editing"
                        @click="saveComment(comment, idx)"
                    >保存</a-button>
                  </a-col>
                </a-row>
              </a-list-item>
            </a-list>
          </div>

          <!-- 评论输入框 -->
          <div class="reply-box">
            <a-input
                v-model:value="replyText"
                placeholder="写评论..."
                @pressEnter="sendComment"
                allowClear
            />
            <a-button type="primary" @click="sendComment">发送</a-button>
            <div class="post-actions">
              <div class="actions">
                <!-- 爱心按钮 -->
                <div class="action-item" @click="toggleLike">
                  <img :src="liked ? '../../../public/img/爱心-选中.png' : '../../../public/img/爱心.png'" alt="爱心" />
                  <span class="like-count">{{ likecount }}</span>
                </div>

                <!-- 收藏按钮 -->
                <div class="action-item" @click="toggleFavorite">
                  <img :src="favorited ? '../../../public/img/收藏-选中.png' : '../../../public/img/收藏.png'" alt="收藏" />
                  <span class="like-count">{{ favoritedcount }}</span>
                </div>
              </div>
            </div>
          </div>


        </div>
      </a-col>
    </a-row>
  </a-modal>
</template>

<script setup>
import {ref, computed, watch} from 'vue'
import {message, notification} from 'ant-design-vue'
import dayjs from 'dayjs'
import axios from "axios"
import store from "../../store/index.js"
import {useRouter} from "vue-router";


const router = useRouter()

const open = ref(false)
const currentCard = ref({ images: [], comments: [] })
const commentList = ref([]);
const baseUrl = 'http://localhost:8080/lyw'

const currentIndex = ref(0)
let timer = null
const isFollowed = ref(false)

const liked = ref(false);
const favorited = ref(false);

const likeflist = ref([]);
const likecount = ref(0);
const favoritedcount = ref(0);

function goAuthorPage() {
  if(currentCard.value?.userId){
    console.log('写入 authorId:', currentCard.value.userId)
    sessionStorage.setItem('authorId', currentCard.value.userId)
    router.push('/AuthorDetail')
  } else {
    message.error("用户ID不存在")
  }
}


const handleOk = () => {
  open.value = false
  stopAutoPlay()
}

const deleteComment = (comment, idx) => {
  axios.post(`http://localhost:8080/lyw/web/comment/del-comment`, {
    id: comment.commentId
  }).then(res => {
    if (res.data.success) {
      message.success('删除成功')
      commentList.value.splice(idx, 1)
    } else {
      message.error(res.data.message)
    }
  }).catch(() => {
    message.error('请求失败')
  })
}


const showModal = (item) => {
  currentCard.value = item
  currentIndex.value = 0
  open.value = true
  console.log("接收到的数据:", item)
  currentCard.value = item
  axios.get("http://localhost:8080/lyw/web/comment/findall-comment", {
    params: {
      postId: currentCard.value.postId
    }
  })
      .then((response) => {
        const data = response.data;
        if (data.success) {
          commentList.value = (data.content || []).map((c) => ({
            username: c.membername,
            content: c.commentContent,
            time: c.createTime || c.time,
            commentId : c.id,
            editing: false,        // 是否正在编辑
            editContent: '',       // 编辑内容
            avatar: c.avatar
          }));
        } else {
          notification.error({ description: data.message });
        }
      })
      .catch(() => {
        notification.error({ description: "请求失败" });
      });

  axios.post("http://localhost:8080/lyw/web/userfollow/find-user-follow", {
    userId: member.value?.id,
    followId: currentCard.value.userId
  }).then(response => {
    const data = response.data;
    if (data.success) {
      // 判断是否已关注
      isFollowed.value = data.content;
      console.log("是否已关注：", isFollowed.value);
    } else {
      message.error(data.message);
    }
  }).catch(() => {
    message.error("请求失败");
  });


  axios.post("http://localhost:8080/lyw/web/userAction/findUserAction", {
    postId: currentCard.value.postId
  })
      .then(response => {
        const data = response.data;
        if (data.success) {
          likeflist.value = data.content;
          console.log("返回的用户操作列表:", likeflist.value);
          initLikeAndFavorite();
        } else {
          message.error(data.message)
        }
      })
      .catch(err => {
        message.error("请求失败：" + err)
      });

  axios.post("http://localhost:8080/lyw/web/userAction/PostUserLikeActionCount", {
    postId: currentCard.value.postId
  })
      .then(response => {
        const data = response.data;
        if (data.success) {
          likecount.value = Number(data.content);
          console.log("返回的用户统计列表:", likecount.value);
        } else {
          message.error(data.message)
        }
      })
      .catch(err => {
        message.error("请求失败：" + err)
      });

  axios.post("http://localhost:8080/lyw/web/userAction/PostUserFavoritedcountActionCount", {
    postId: currentCard.value.postId
  })
      .then(response => {
        const data = response.data;
        if (data.success) {
          favoritedcount.value = Number(data.content);
          console.log("返回的用户统计列表:", favoritedcount.value);
        } else {
          message.error(data.message)
        }
      })
      .catch(err => {
        message.error("请求失败：" + err)
      });
}

const member = computed(() => store.state.member)
const replyText = ref()

const sendComment = () => {
  if (!replyText.value) {
    message.warning('评论内容不能为空')
    return
  }

  const newCommentContent = replyText.value

  axios.post('http://localhost:8080/lyw/web/comment/save-comment', {
    postId: currentCard.value.postId,
    userId: member.value?.id,
    content: newCommentContent
  }).then(res => {
    if (res.data.success) {
      message.success('评论成功')
      // 直接更新 commentList.value
      commentList.value.push({
        username: member.value.username,
        content: newCommentContent,
        time: new Date()
      })

      replyText.value = ''
    } else {
      message.error(res.data.message)
    }
  })
}

// 点击编辑按钮
const updateComment = (comment, idx) => {
  comment.editing = !comment.editing;
  if (comment.editing) {
    comment.editContent = comment.content; // 显示原评论
  } else {
    comment.editContent = '';
  }
}

// 保存修改
const saveComment = (comment, idx) => {
  if (!comment.editContent) {
    message.warning('评论内容不能为空')
    return;
  }
  axios.post('http://localhost:8080/lyw/web/comment/update-comment', {
    id: comment.commentId,
    content: comment.editContent
  }).then(res => {
    if (res.data.success) {
      comment.content = comment.editContent;
      comment.editing = false;
      comment.editContent = '';
      message.success('修改成功');
    } else {
      message.error(res.data.message);
    }
  }).catch(() => {
    message.error('请求失败');
  });
}

const handleFollow = async () => {

  if (!isFollowed.value) {
  axios.post("http://localhost:8080/lyw/web/userfollow/save-user-follow", {
    userId: member.value?.id,
    followId: currentCard.value.userId
  }).then(response => {
    const data = response.data;
    if (data.success) {
      isFollowed.value = true; // ✅ 更新状态
      message.success("关注成功");
    } else {
      message.error(data.message);
    }
  }).catch(() => {
    message.error("请求失败");
  });}
  else {
    axios.post("http://localhost:8080/lyw/web/userfollow/unfollow-user-follow", {
      userId: member.value?.id,
      followId: currentCard.value.userId
    }).then(response => {
      const data = response.data;
      if (data.success) {
        isFollowed.value = false;
        message.success("取消关注成功");
      } else {
        message.error(data.message);
      }
    }).catch(() => {
      message.error("请求失败");
    });
  }
}

function toggleLike() {
  const actionType = "like";
  if (liked.value) {
    // 删除点赞
    axios.post("http://localhost:8080/lyw/web/userAction/delUserAction", {
      postId: currentCard.value.postId,
      actionType
    }).then(res => {
      if (res.data.success) {
        liked.value = false;
        likecount.value = likecount.value-1
        message.success("取消点赞成功");
      }
    });
  } else {
    // 新增点赞
    axios.post("http://localhost:8080/lyw/web/userAction/insertUserAction", {
      postId: currentCard.value.postId,
      actionType
    }).then(res => {
      if (res.data.success) {
        liked.value = true;
        likecount.value = likecount.value+1
        message.success("点赞成功");
      }
    });
  }
}


function toggleFavorite() {
  const actionType = "favorite";
  if (!favorited.value){
    axios.post("http://localhost:8080/lyw/web/userAction/insertUserAction", {
      postId: currentCard.value.postId,
      actionType
    }).then(res => {
      if (res.data.success) {
        favorited.value = true;
        favoritedcount.value += 1; // ✅ 增加收藏数
        message.success("收藏成功" );
      }
    })
  } else {
    axios.post("http://localhost:8080/lyw/web/userAction/delUserAction", {
      postId: currentCard.value.postId,
      actionType
    }).then(res => {
      if (res.data.success) {
        favorited.value = false;
        favoritedcount.value -= 1; // ✅ 减少收藏数
        message.success("取消收藏");
      }
    })
  }
}


function initLikeAndFavorite() {
  const postId = currentCard.value.postId;

  liked.value = likeflist.value.some(item => String(item.postId) === String(postId) && item.actionType === "like");
  favorited.value = likeflist.value.some(item => String(item.postId) === String(postId) && item.actionType === "favorite");


  console.log("likeflist:", likeflist.value, "postId:", postId)
}

// 轮播控制
const prev = () => {
  currentIndex.value = (currentIndex.value - 1 + currentCard.value.images.length) % currentCard.value.images.length
}
const next = () => {
  currentIndex.value = (currentIndex.value + 1) % currentCard.value.images.length
}
const goTo = (index) => {
  currentIndex.value = index
}
const stopAutoPlay = () => {
  if (timer) clearInterval(timer)
}
watch(open, (val) => { if (!val) stopAutoPlay() })
defineExpose({ showModal })
</script>

<style scoped>
/* 左侧轮播图 */
.carousel-container {
  position: relative;
  overflow: hidden;
  height: 700px;
  border-radius: 8px;
}
.carousel-track {
  display: flex;
  transition: transform 0.3s ease;
  height: 100%;
}
.carousel-item {
  min-width: 100%;
}
.carousel-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
}
.carousel-prev, .carousel-next {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  font-size: 24px;
  color: #fff;
  background: rgba(0,0,0,0.3);
  border: none;
  padding: 4px 8px;
  cursor: pointer;
  border-radius: 4px;
}
.carousel-prev { left: 8px; }
.carousel-next { right: 8px; }
.carousel-dots {
  position: absolute;
  bottom: 8px;
  width: 100%;
  display: flex;
  justify-content: center;
  gap: 6px;
}
.carousel-dots span {
  width: 8px;
  height: 8px;
  background: rgba(255,255,255,0.5);
  border-radius: 50%;
  cursor: pointer;
}
.carousel-dots span.active { background: #fff; }

/* 右侧内容 */
.right-content {
  height: 700px;
  display: flex;
  flex-direction: column;
  overflow-y: auto; /* 整个右侧统一滚动 */
  padding-right: 8px; /* 给滚动条留空间 */
}
.user-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.user-left {
  display: flex;
  align-items: center;
  gap: 12px; /* 头像和文字间距 */
}

.user-text {
  display: flex;
  flex-direction: column;
}

.username {
  font-weight: bold;
  font-size: 20px;
}

.user-id {
  font-size: 14px;
  color: #999;
}

.post-content {
  max-height: none;
  overflow: visible;
  margin-bottom: 8px;
}
.post-content p {
  white-space: pre-wrap;
  font-size: 15px;
}
.post-content::-webkit-scrollbar {
  width: 6px;
}

.post-content::-webkit-scrollbar-thumb {
  background-color: rgba(0,0,0,0.2);
  border-radius: 3px;
}
.post-content::-webkit-scrollbar-track {
  background: transparent;
}

.post-time { color: #999; }

.comments-section {
  flex: none; /* 不占 flex 剩余空间滚动 */
  overflow: visible;
  margin-bottom: 8px;
}
.comments-section::-webkit-scrollbar {
  width: 6px;
}
.comments-section::-webkit-scrollbar-thumb {
  background-color: rgba(0,0,0,0.2);
  border-radius: 3px;
}
.comments-section::-webkit-scrollbar-track {
  background: transparent;
}

.reply-box {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.comment-time {
  font-size: 12px;
  color: #999;
  flex-shrink: 0; /* 不压缩 */
  white-space: nowrap; /* 防止换行 */
}
.comment-user { font-weight: bold; font-size: 20px}
.comment-text { white-space: pre-wrap; font-size: 20px}

.actions {
  display: flex;
  gap: 20px; /* 按钮间距 */
}

.action-item {
  display: flex;
  flex-direction: column; /* 图标在上，数字在下 */
  align-items: center;
  justify-content: center;
  cursor: pointer;
  width: 50px;
  height: 50px;
  border-radius: 50%;
  transition: transform 0.2s, background-color 0.2s;
}

.action-item:hover {
  transform: scale(1.1);
  background-color: #f0f0f0;
}

.action-item img {
  width: 30px;
  height: 30px;
}

.like-count {
  margin-top: 4px;
  font-size: 14px;
  font-weight: bold;
  color: #333;
}

</style>
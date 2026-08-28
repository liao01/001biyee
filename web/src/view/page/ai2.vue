<template>
  <div class="chat-wrapper">
    <div class="chat-box">
      <div class="chat-header">
        <div>
          <span class="bot-name">旅游助手</span>
          <p>帮你梳理目的地、路线和出行准备</p>
        </div>
        <span class="status"><i class="status-dot"></i> 在线</span>
      </div>

      <div class="messages" ref="messaggListRef">
        <div
            v-for="(message, index) in messages"
            :key="index"
            :class="message.isUser ? 'msg msg-user' : 'msg msg-bot'"
        >
          <div class="avatar">
            <img
                :src="message.isUser ? avatarUrl : '/img/Robot.png'"
                class="avatar-img"
                alt="对话头像"
            >
          </div>

          <div class="bubble-container">
            <div class="bubble">
              <div v-html="message.content" class="content-text"></div>

              <div class="typing" v-if="message.isTyping">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input-container">
        <div class="chat-input-wrapper">
          <a-textarea
              v-model:value="inputMessage"
              placeholder="问问我任何问题..."
              :auto-size="{ minRows: 1, maxRows: 4 }"
              @pressEnter.prevent="sendMessage"
              class="custom-textarea"
          />
          <div class="input-actions">
            <a-button
                type="primary"
                shape="circle"
                :loading="isSending"
                @click="sendMessage"
                class="send-btn"
            >
              <template #icon><SendOutlined /></template>
            </a-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import axios from 'axios'
import { message } from 'ant-design-vue'
import { SendOutlined } from '@ant-design/icons-vue'
import { v4 as uuidv4 } from 'uuid'
import { BASE_URL } from "../../utils/baseUrl";

const messaggListRef = ref()
const isSending = ref(false)
const uuid = ref()
const inputMessage = ref('')
const messages = ref([])

onMounted(() => {
  initUUID()
  // 移除 setInterval，改用手动滚动
  watch(messages, () => scrollToBottom(), { deep: true })
  hello()
  fetchAvatar()
})

const scrollToBottom = () => {
  if (messaggListRef.value) {
    messaggListRef.value.scrollTop = messaggListRef.value.scrollHeight
  }
}

const hello = () => {
  sendRequest('你好')
}

const sendMessage = () => {
  if (inputMessage.value.trim()) {
    sendRequest(inputMessage.value.trim())
    inputMessage.value = ''
  }
}

const sendRequest = message => {
  isSending.value = true
  const userMsg = {
    isUser: true,
    content: message,
    isTyping: false,
    isThinking: false
  }
  //第一条默认发送的用户消息”你好“不放入会话列表
  if (messages.value.length > 0) {
    messages.value.push(userMsg)
  }

  // 添加机器人加载消息
  const botMsg = {
    isUser: false,
    content: '', // 增量填充
    isTyping: true, // 显示加载动画
    isThinking: false
  }
  messages.value.push(botMsg)
  const lastMsg = messages.value[messages.value.length - 1]
  scrollToBottom()

  axios
      .post(
          BASE_URL+'/lyw/web/xiaozhi/chat',
          { memoryId: uuid.value, message },
          {
            responseType: 'stream', // 必须为合法值 "text"
            onDownloadProgress: e => {
              const fullText = e.event.target.responseText // 累积的完整文本
              let newText = fullText.substring(lastMsg.content.length)
              lastMsg.content += newText //增量更新
              console.log(lastMsg)
              scrollToBottom() // 实时滚动
            }
          }
      )
      .then(() => {
        // 流结束后隐藏加载动画
        messages.value.at(-1).isTyping = false
        isSending.value = false
      })
      .catch(error => {
        console.error('流式错误:', error)
        messages.value.at(-1).content = '请求失败，请重试'
        messages.value.at(-1).isTyping = false
        isSending.value = false
      })
}

// 初始化 UUID
const initUUID = () => {
  let storedUUID = localStorage.getItem('user_uuid')
  if (!storedUUID) {
    storedUUID = uuidToNumber(uuidv4())
    localStorage.setItem('user_uuid', storedUUID)
  }
  uuid.value = storedUUID
}

const uuidToNumber = uuid => {
  let number = 0
  for (let i = 0; i < uuid.length && i < 6; i++) {
    const hexValue = uuid[i]
    number = number * 16 + (parseInt(hexValue, 16) || 0)
  }
  return number % 1000000
}

const avatarUrl = ref('')

// 请求用户头像
const fetchAvatar = async () => {
  axios.get(BASE_URL+"/lyw/web/UserProFile/findAvatarUser").then(response => {
    const data = response.data;
    if (data.success) {
      avatarUrl.value = BASE_URL+`/lyw${data.content}`
    } else {
      message.error(data.message)
    }
  })
}
</script>

<style scoped>
/* 全局容器背景 */
.chat-wrapper {
  width: 100%;
  height: calc(100vh - var(--travel-header-height));
  padding: 28px clamp(18px, 4vw, 56px);
  background-color: var(--travel-color-bg-subtle);
  display: flex;
  justify-content: center;
}

.chat-box {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 1060px;
  height: 100%;
  background: #ffffff;
  border: 1px solid var(--travel-color-border);
  border-radius: var(--travel-radius-lg);
  box-shadow: none;
  overflow: hidden;
}

/* 页眉样式 */
.chat-header {
  padding: 20px 24px;
  border-bottom: 1px solid var(--travel-color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.status-dot {
  width: 8px;
  height: 8px;
  background-color: #52c41a;
  border-radius: 50%;
  display: inline-block;
  margin-right: 5px;
}

.bot-name {
  font-weight: 600;
  color: #1f1f1f;
  font-size: 16px;
}
.chat-header p {
  color: var(--travel-color-text-muted);
  font-size: 12px;
  margin: 4px 0 0;
}
.status { color: var(--travel-color-text-secondary); font-size: 12px; }

/* 消息列表区域 */
.messages {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  background-color: #fff;
  scroll-behavior: smooth;
}

.msg {
  display: flex;
  margin-bottom: 24px;
  max-width: 90%;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.msg-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.msg-bot {
  align-self: flex-start;
}

/* 头像优化 */
.avatar {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 50%;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 气泡美化 */
.bubble-container {
  display: flex;
  flex-direction: column;
  margin: 0 12px;
}

.bubble {
  padding: 12px 16px;
  border-radius: 18px;
  line-height: 1.6;
  font-size: 15px;
  position: relative;
  transition: all 0.2s;
}

.msg-bot .bubble {
  background: #ffffff;
  color: #333;
  border: 1px solid #e8e8e8;
  border-top-left-radius: 4px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.02);
}

.msg-user .bubble {
  background: var(--travel-color-brand);
  color: #fff;
  border-top-right-radius: 4px;
  box-shadow: none;
}

.content-text :deep(p) {
  margin-bottom: 8px;
}

/* 输入框容器 */
.chat-input-container {
  padding: 20px 24px;
  background: #fff;
  border-top: 1px solid #f0f0f0;
}

.chat-input-wrapper {
  display: flex;
  align-items: flex-end;
  background: #f5f5f5;
  border-radius: 12px;
  padding: 8px 12px;
  transition: border 0.3s, background 0.3s;
}

.chat-input-wrapper:focus-within {
  background: #fff;
  border: 1px solid var(--travel-color-brand);
  box-shadow: 0 0 0 3px rgb(255 59 79 / 10%);
}

.custom-textarea {
  flex: 1;
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  padding: 8px 0;
  font-size: 15px;
  resize: none;
}

.input-actions {
  margin-left: 12px;
  padding-bottom: 4px;
}

.send-btn {
  width: 36px !important;
  height: 36px !important;
  display: flex;
  align-items: center;
  justify-content: center;
}
:deep(.send-btn.ant-btn-primary) {
  background: var(--travel-color-brand);
  border-color: var(--travel-color-brand);
}

/* Typing 动画微调 */
.typing {
  display: flex;
  align-items: center;
  height: 20px;
}

.dot {
  width: 5px;
  height: 5px;
  background: var(--travel-color-brand);
  border-radius: 50%;
  margin-right: 3px;
  opacity: 0.6;
}

@media (max-width: 767px) {
  .chat-wrapper {
    height: calc(100vh - 64px - 68px);
    padding: 0;
  }
  .chat-box { border: 0; border-radius: 0; }
  .messages { padding: 18px 14px; }
  .msg { max-width: 100%; }
  .chat-input-container { padding: 12px 14px; }
}
</style>

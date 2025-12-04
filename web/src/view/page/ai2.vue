<template>
  <div class="chat-wrapper">
    <div class="chat-box">
      <div class="messages" ref="messaggListRef">
        <div
            v-for="(message, index) in messages"
            :key="index"
            :class="message.isUser ? 'msg msg-user' : 'msg msg-bot'"
        >
          <!-- 头像 -->
          <div class="avatar">
            <i :class="message.isUser ? 'fa-solid fa-user' : 'fa-solid fa-robot'"></i>
          </div>

          <!-- 气泡内容 -->
          <div class="bubble">
            <div v-html="message.content"></div>

            <!-- typing 动画 -->
            <div class="typing" v-if="message.isTyping">
              <span class="dot"></span>
              <span class="dot"></span>
              <span class="dot"></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入框 -->
      <div class="chat-input">
        <a-input
            v-model:value="inputMessage"
            placeholder="请输入消息"
            @pressEnter="sendMessage"
        />

        <a-button
            type="primary"
            :loading="isSending"
            @click="sendMessage"
        >
          发送
        </a-button>
      </div>
    </div>
  </div>
</template>


<script setup>
import { onMounted, ref, watch } from 'vue'
import axios from 'axios'
import { v4 as uuidv4 } from 'uuid'

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
          'http://localhost:8080/lyw/web/xiaozhi/chat',
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

</script>

<style scoped>
.chat-wrapper {
  width: 100%;
  height: 100%;
  padding: 10px;
}

.chat-box {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}

/* 消息列表 */
.messages {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

/* 单条消息 */
.msg {
  display: flex;
  margin-bottom: 14px;
  max-width: 85%;
}

.msg-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.msg-bot {
  align-self: flex-start;
}

/* 头像 */
.avatar {
  width: 32px;
  height: 32px;
  background: #e5e7eb;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 10px;
  font-size: 16px;
}

/* 气泡 */
.bubble {
  padding: 12px 15px;
  border-radius: 10px;
  background: #f6f6f6;
  line-height: 1.6;
  font-size: 14px;
}

.msg-user .bubble {
  background: #1677ff;
  color: #fff;
}

/* 输入框区 */
.chat-input {
  display: flex;
  padding: 10px;
  gap: 10px;
}

/* typing 动画 */
.typing {
  display: flex;
  margin-top: 4px;
}

.dot {
  width: 6px;
  height: 6px;
  background: #999;
  border-radius: 50%;
  margin-right: 4px;
  animation: blink 1s infinite alternate;
}

.dot:nth-child(2) {
  animation-delay: 0.2s;
}
.dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes blink {
  from { opacity: 0.2; }
  to { opacity: 1; }
}
</style>

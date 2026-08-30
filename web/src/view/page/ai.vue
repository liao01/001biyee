<template>
  <div class="chat-container">
    <h2>旅游助手</h2>

    <!-- 聊天记录 -->
    <div class="chat-log" ref="chatLog">
      <div
          v-for="(item, index) in chatHistory"
          :key="index"
          :class="['chat-item', item.user === '你' ? 'user' : 'ai']"
      >
        <div class="bubble">
          <p>{{ item.message }}</p>
        </div>
      </div>
    </div>

    <!-- 输入框区域 -->
    <div class="input-area">
      <textarea
          v-model="userMessage"
          placeholder="请输入消息..."
          rows="2"
          @keyup.enter.exact.prevent="sendMessage"
      ></textarea>
      <button @click="sendMessage">发送</button>
    </div>
  </div>
</template>

<script setup>
import {ref, nextTick, onMounted} from 'vue';
import axios from 'axios';
import { buildApiUrl } from '../../utils/baseUrl.js';

const userMessage = ref('');
const chatHistory = ref([]);
const chatLog = ref(null);

const sendMessage = async () => {
  if (!userMessage.value.trim()) return;

  // 添加用户消息
  chatHistory.value.push({ user: '你', message: userMessage.value });
  const messageToSend = userMessage.value;
  userMessage.value = '';

  // 滚动到底部
  await nextTick();
  chatLog.value.scrollTop = chatLog.value.scrollHeight;

  try {
    const res = await axios.post(buildApiUrl('/lyw/web/customerService/message'), {
      sessionId: '123456',
      message: messageToSend,
    });

    const aiReply = res.data.data;

    chatHistory.value.push({ user: 'AI', message: aiReply });

    await nextTick();
    chatLog.value.scrollTop = chatLog.value.scrollHeight;
  } catch (err) {
    console.error(err);
    chatHistory.value.push({ user: 'AI', message: '调用 AI 接口失败，请稍后重试' });

    await nextTick();
    chatLog.value.scrollTop = chatLog.value.scrollHeight;
  }
};


const loadChatHistory = async () => {
  try {
    const res = await axios.get(buildApiUrl('/lyw/web/customerService/history'));
    const data = res.data.data || [];

    // 转换成前端渲染格式
    chatHistory.value = [];
    data.forEach(item => {
      if (item.userMessage) {
        chatHistory.value.push({ user: '你', message: item.userMessage });
      }
      if (item.aiResponse) {
        chatHistory.value.push({ user: 'AI', message: item.aiResponse });
      }
    });

    await nextTick();
    chatLog.value.scrollTop = chatLog.value.scrollHeight;
  } catch (err) {
    console.error(err);
  }
};

onMounted(() => {
  loadChatHistory();
});
</script>

<style scoped>
.chat-container {
  max-width: 1200px;
  margin: 20px auto;
  border: 1px solid #eee;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  height: 800px;
  background-color: #fff;
}

h2 {
  text-align: center;
  padding: 12px 0;
  border-bottom: 1px solid #eee;
  font-weight: bold;
  color: #333;
}

.chat-log {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background-color: #f9f9f9;
}

.chat-item {
  display: flex;
  max-width: 80%;
}

.chat-item.user {
  justify-content: flex-end;
}

.chat-item.ai {
  justify-content: flex-start;
}

.bubble {
  padding: 8px 12px;
  border-radius: 16px;
  word-break: break-word;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.chat-item.user .bubble {
  background-color: #4f97ff;
  color: #fff;
  border-bottom-right-radius: 2px;
}

.chat-item.ai .bubble {
  background-color: #f1f0f0;
  color: #333;
  border-bottom-left-radius: 2px;
}

.input-area {
  display: flex;
  padding: 8px;
  border-top: 1px solid #eee;
  gap: 8px;
}

textarea {
  flex: 1;
  padding: 8px;
  border-radius: 8px;
  border: 1px solid #ccc;
  resize: none;
}

button {
  padding: 8px 16px;
  border: none;
  background-color: #4f97ff;
  color: #fff;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
}

button:hover {
  background-color: #3c7dde;
}
</style>

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
import { ref, nextTick } from 'vue';

const userMessage = ref('');
const chatHistory = ref([]);
const chatLog = ref(null);

const sendMessage = async () => {
  if (!userMessage.value.trim()) return;

  // 添加用户消息
  chatHistory.value.push({ user: '你', message: userMessage.value });
  const messageToSend = userMessage.value;
  userMessage.value = '';

  await nextTick();
  chatLog.value.scrollTop = chatLog.value.scrollHeight;

  try {
    const response = await fetch('http://localhost:8080/lyw/web/xiaozhi/chat', {
      method: 'POST',
      body: JSON.stringify({
        memoryId: 1,
        message: messageToSend
      })
    });

    if (!response.body) throw new Error('响应体为空');

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop(); // 保留最后可能不完整的一行

      lines.forEach(line => {
        if (line.trim()) {
          chatHistory.value.push({ user: 'AI', message: line.trim() });
        }
      });

      await nextTick();
      chatLog.value.scrollTop = chatLog.value.scrollHeight;
    }

    // 处理最后一行
    if (buffer.trim()) {
      chatHistory.value.push({ user: 'AI', message: buffer.trim() });
      await nextTick();
      chatLog.value.scrollTop = chatLog.value.scrollHeight;
    }

  } catch (err) {
    console.error(err);
    chatHistory.value.push({ user: 'AI', message: '调用 AI 接口失败，请稍后重试' });
    await nextTick();
    chatLog.value.scrollTop = chatLog.value.scrollHeight;
  }
};
</script>

<style scoped>
.chat-container {
  max-width: 800px;
  margin: 20px auto;
  border: 1px solid #eee;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  height: 700px;
  background-color: #fff;
  font-family: "Microsoft YaHei", sans-serif;
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
  scrollbar-width: thin;
  scrollbar-color: #ccc transparent;
}

.chat-log::-webkit-scrollbar {
  width: 6px;
}

.chat-log::-webkit-scrollbar-thumb {
  background-color: #ccc;
  border-radius: 3px;
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
  padding: 10px 16px;
  border-radius: 16px;
  word-break: break-word;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  line-height: 1.4;
  white-space: pre-wrap;
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
  font-size: 14px;
  line-height: 1.4;
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

<template>
  <div class="love-master-container">
    <div class="header">
      <div class="back-button" @click="goBack">返回</div>
      <h1 class="title">AI恋爱大师</h1>
      <div class="placeholder"></div>
    </div>

    <div class="content-wrapper">
      <!-- 左侧历史对话面板 -->
      <div class="history-panel">
        <ChatHistory
          :sessions="sessions"
          :current-session-id="currentSessionId"
          @new-chat="handleNewChat"
          @select="handleSelectSession"
          @delete="handleDeleteSession"
          @clear="handleClearSessions"
        />
      </div>

      <!-- 右侧聊天区域 -->
      <div class="chat-area">
        <ChatRoom
          :messages="messages"
          :connection-status="connectionStatus"
          ai-type="love"
          @send-message="sendMessage"
        />
      </div>
    </div>

    <div class="footer-container">
      <AppFooter />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import ChatHistory from '../components/ChatHistory.vue'
import AppFooter from '../components/AppFooter.vue'
import { chatWithLoveApp } from '../api'
import {
  CHAT_TYPES,
  getSessions,
  createSession,
  getSession,
  addMessageToSession,
  deleteSession,
  clearSessions,
  updateSession
} from '../utils/chatStorage'

// 当前会话ID存储键
const CURRENT_SESSION_KEY = 'ai_chat_love_current_session'

// 设置页面标题和元数据
useHead({
  title: 'AI恋爱大师 - AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: 'AI恋爱大师是AI超级智能体应用平台的专业情感顾问，帮你解答各种恋爱问题，提供情感建议'
    },
    {
      name: 'keywords',
      content: 'AI恋爱大师,情感顾问,恋爱咨询,AI聊天,情感问题,AI智能体'
    }
  ]
})

const router = useRouter()
const messages = ref([])
const currentSessionId = ref('')
const connectionStatus = ref('disconnected')
const sessions = ref([])
const isNewChat = ref(false) // 标记是否为新对话状态
let eventSource = null

// 加载会话列表
const loadSessions = () => {
  sessions.value = getSessions(CHAT_TYPES.LOVE)
}

// 保存当前会话ID
const saveCurrentSessionId = () => {
  if (currentSessionId.value) {
    localStorage.setItem(CURRENT_SESSION_KEY, currentSessionId.value)
  } else {
    localStorage.removeItem(CURRENT_SESSION_KEY)
  }
}

// 新建对话
const handleNewChat = () => {
  // 清空当前状态
  messages.value = []
  currentSessionId.value = ''
  isNewChat.value = true
  saveCurrentSessionId()

  // 添加欢迎消息
  messages.value = [{
    content: '欢迎来到AI恋爱大师，请告诉我你的恋爱问题，我会尽力给予帮助和建议。',
    isUser: false,
    time: Date.now()
  }]
}

// 选择会话
const handleSelectSession = (sessionId) => {
  const session = getSession(CHAT_TYPES.LOVE, sessionId)
  if (session && session.messages) {
    currentSessionId.value = sessionId
    isNewChat.value = false
    // 深拷贝消息数组确保响应式更新
    messages.value = JSON.parse(JSON.stringify(session.messages))
    saveCurrentSessionId()
  }
}

// 删除会话
const handleDeleteSession = (sessionId) => {
  deleteSession(CHAT_TYPES.LOVE, sessionId)
  loadSessions()

  // 如果删除的是当前会话，创建新对话
  if (sessionId === currentSessionId.value) {
    handleNewChat()
  }
}

// 清空所有会话
const handleClearSessions = () => {
  clearSessions(CHAT_TYPES.LOVE)
  loadSessions()
  handleNewChat()
}

// 添加消息到列表
const addMessage = (content, isUser, isStreaming = false) => {
  const message = {
    content,
    isUser,
    isStreaming,
    time: new Date().getTime()
  }
  messages.value.push(message)

  // 保存到存储
  if (currentSessionId.value) {
    addMessageToSession(CHAT_TYPES.LOVE, currentSessionId.value, message)
    loadSessions()
  } else if (isUser && isNewChat.value) {
    // 用户在新对话中发送第一条消息时创建新会话
    const title = content.substring(0, 20) + (content.length > 20 ? '...' : '')
    const session = createSession(CHAT_TYPES.LOVE, title)
    currentSessionId.value = session.id
    isNewChat.value = false

    // 添加所有消息到新会话
    messages.value.forEach(msg => {
      addMessageToSession(CHAT_TYPES.LOVE, currentSessionId.value, msg)
    })
    saveCurrentSessionId()
    loadSessions()
  }
}

// 发送消息
const sendMessage = (message) => {
  addMessage(message, true)

  // 连接SSE
  if (eventSource) {
    eventSource.close()
  }

  // 创建一个空的AI回复消息（流式传输中）
  const aiMessageIndex = messages.value.length
  addMessage('', false, true)  // isStreaming = true

  connectionStatus.value = 'connecting'
  eventSource = chatWithLoveApp(message, currentSessionId.value)

  // 监听SSE消息
  eventSource.onmessage = (event) => {
    const data = event.data
    if (data && data !== '[DONE]') {
      // 更新最新的AI消息内容
      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].content += data
        // 更新存储中的消息
        if (currentSessionId.value) {
          const session = getSession(CHAT_TYPES.LOVE, currentSessionId.value)
          if (session && session.messages && session.messages[aiMessageIndex]) {
            session.messages[aiMessageIndex].content = messages.value[aiMessageIndex].content
            // 触发保存
            const sessionsList = getSessions(CHAT_TYPES.LOVE)
            const idx = sessionsList.findIndex(s => s.id === currentSessionId.value)
            if (idx !== -1) {
              sessionsList[idx] = session
              localStorage.setItem('ai_chat_love_sessions', JSON.stringify(sessionsList))
            }
          }
        }
      }
    }

    if (data === '[DONE]') {
      // 流式传输完成，关闭isStreaming标志
      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].isStreaming = false
      }
      connectionStatus.value = 'disconnected'
      eventSource.close()
      loadSessions()
    }
  }

  // 监听SSE错误
  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    // 出错时也关闭isStreaming
    if (aiMessageIndex < messages.value.length) {
      messages.value[aiMessageIndex].isStreaming = false
    }
    connectionStatus.value = 'error'
    eventSource.close()
  }
}

// 返回主页
const goBack = () => {
  router.push('/')
}

// 页面加载时初始化
onMounted(() => {
  loadSessions()

  // 尝试恢复上次的会话
  const savedSessionId = localStorage.getItem(CURRENT_SESSION_KEY)
  if (savedSessionId) {
    const session = getSession(CHAT_TYPES.LOVE, savedSessionId)
    if (session && session.messages && session.messages.length > 0) {
      currentSessionId.value = savedSessionId
      isNewChat.value = false
      messages.value = JSON.parse(JSON.stringify(session.messages))
      return
    }
  }

  // 没有保存的会话或会话不存在，创建新对话
  handleNewChat()
})

// 组件销毁前关闭SSE连接
onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
})
</script>

<style scoped>
.love-master-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #fff9f9;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background-color: #ff6b8b;
  color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 10;
}

.back-button {
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: opacity 0.2s;
}

.back-button:hover {
  opacity: 0.8;
}

.back-button:before {
  content: '←';
  margin-right: 8px;
}

.title {
  font-size: 20px;
  font-weight: bold;
  margin: 0;
}

.placeholder {
  width: 60px;
}

.content-wrapper {
  display: flex;
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

.history-panel {
  width: 280px;
  flex-shrink: 0;
  background-color: #fff;
  border-right: 1px solid #e8e8e8;
  overflow: hidden;
}

.chat-area {
  flex: 1;
  padding: 16px;
  overflow: hidden;
  position: relative;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.footer-container {
  margin-top: auto;
}

/* 响应式样式 */
@media (max-width: 768px) {
  .header {
    padding: 12px 16px;
  }

  .title {
    font-size: 18px;
  }

  .history-panel {
    width: 220px;
  }

  .chat-area {
    padding: 12px;
    min-height: calc(100vh - 48px - 50px);
  }
}

@media (max-width: 480px) {
  .header {
    padding: 10px 12px;
  }

  .back-button {
    font-size: 14px;
  }

  .title {
    font-size: 16px;
  }

  .content-wrapper {
    flex-direction: column;
  }

  .history-panel {
    width: 100%;
    height: 200px;
    border-right: none;
    border-bottom: 1px solid #e8e8e8;
  }

  .chat-area {
    padding: 8px;
    min-height: calc(100vh - 42px - 200px - 50px);
  }
}
</style>

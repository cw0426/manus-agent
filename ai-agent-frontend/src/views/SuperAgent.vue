<template>
  <div class="super-agent-container">
    <div class="header">
      <div class="back-button" @click="goBack">返回</div>
      <h1 class="title">AI超级智能体</h1>
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
          ai-type="super"
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
import { chatWithManus } from '../api'
import {
  CHAT_TYPES,
  getSessions,
  createSession,
  getSession,
  addMessageToSession,
  deleteSession,
  clearSessions
} from '../utils/chatStorage'

// 当前会话ID存储键
const CURRENT_SESSION_KEY = 'ai_chat_super_current_session'

// 设置页面标题和元数据
useHead({
  title: 'AI超级智能体 - AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: 'AI超级智能体是AI超级智能体应用平台的全能助手，能解答各类专业问题，提供精准建议和解决方案'
    },
    {
      name: 'keywords',
      content: 'AI超级智能体,智能助手,专业问答,AI问答,专业建议,AI智能体'
    }
  ]
})

const router = useRouter()
const messages = ref([])
const currentSessionId = ref('')
const connectionStatus = ref('disconnected')
const sessions = ref([])
const isNewChat = ref(false)
let eventSource = null

// 加载会话列表
const loadSessions = () => {
  sessions.value = getSessions(CHAT_TYPES.SUPER)
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
  messages.value = []
  currentSessionId.value = ''
  isNewChat.value = true
  saveCurrentSessionId()

  // 添加欢迎消息
  messages.value = [{
    content: '你好，我是AI超级智能体。我可以解答各类问题，提供专业建议，请问有什么可以帮助你的吗？',
    isUser: false,
    type: '',
    time: Date.now()
  }]
}

// 选择会话
const handleSelectSession = (sessionId) => {
  const session = getSession(CHAT_TYPES.SUPER, sessionId)
  if (session && session.messages) {
    currentSessionId.value = sessionId
    isNewChat.value = false
    messages.value = JSON.parse(JSON.stringify(session.messages))
    saveCurrentSessionId()
  }
}

// 删除会话
const handleDeleteSession = (sessionId) => {
  deleteSession(CHAT_TYPES.SUPER, sessionId)
  loadSessions()

  if (sessionId === currentSessionId.value) {
    handleNewChat()
  }
}

// 清空所有会话
const handleClearSessions = () => {
  clearSessions(CHAT_TYPES.SUPER)
  loadSessions()
  handleNewChat()
}

// 添加消息到列表
const addMessage = (content, isUser, type = '', isStreaming = false) => {
  const message = {
    content,
    isUser,
    type,
    isStreaming,
    time: new Date().getTime()
  }
  messages.value.push(message)

  // 保存到存储
  if (currentSessionId.value) {
    addMessageToSession(CHAT_TYPES.SUPER, currentSessionId.value, message)
    loadSessions()
  } else if (isUser && isNewChat.value) {
    // 用户在新对话中发送第一条消息时创建新会话
    const title = content.substring(0, 20) + (content.length > 20 ? '...' : '')
    const session = createSession(CHAT_TYPES.SUPER, title)
    currentSessionId.value = session.id
    isNewChat.value = false

    // 添加所有消息到新会话
    messages.value.forEach(msg => {
      addMessageToSession(CHAT_TYPES.SUPER, currentSessionId.value, msg)
    })
    saveCurrentSessionId()
    loadSessions()
  }
}

// 解析结构化 SSE 数据
const parseSseData = (rawData) => {
  try {
    const parsed = JSON.parse(rawData)
    if (parsed.type && parsed.content !== undefined) {
      return parsed
    }
  } catch (e) {
    // 不是 JSON 格式，作为纯文本处理
  }
  return { type: 'unknown', content: rawData }
}

// 发送消息
const sendMessage = (message) => {
  addMessage(message, true, 'user-question')

  // 连接SSE
  if (eventSource) {
    eventSource.close()
  }

  connectionStatus.value = 'connecting'

  let currentToolCallIndex = -1

  eventSource = chatWithManus(message)

  eventSource.onmessage = (event) => {
    const rawData = event.data

    if (!rawData || rawData === '[DONE]') {
      // 完成当前正在流式传输的气泡
      if (currentToolCallIndex >= 0 && messages.value[currentToolCallIndex]) {
        messages.value[currentToolCallIndex].isStreaming = false
      }
      connectionStatus.value = 'disconnected'
      eventSource.close()
      loadSessions()
      return
    }

    // 解析结构化 SSE 数据
    const { type, content, toolCalls } = parseSseData(rawData)

    if (type === 'tool_call') {
      // 工具调用：显示简要调用信息
      if (currentToolCallIndex >= 0 && messages.value[currentToolCallIndex]) {
        // 上一个工具调用气泡完成流式传输
        messages.value[currentToolCallIndex].isStreaming = false
      }
      // 添加消息，包含 toolCalls 信息
      const msgData = {
        content,
        isUser: false,
        type: 'ai-tool-call',
        isStreaming: true,
        time: Date.now()
      }
      // 如果有 toolCalls 数据，添加到消息中
      if (toolCalls && toolCalls.length > 0) {
        msgData.toolCalls = toolCalls
      }
      messages.value.push(msgData)
      currentToolCallIndex = messages.value.length - 1

      // 保存到存储
      saveCurrentMessage(msgData)
    } else if (type === 'final_answer') {
      // 最终回答：显示 LLM 的回复
      if (currentToolCallIndex >= 0 && messages.value[currentToolCallIndex]) {
        // 工具调用气泡完成流式传输
        messages.value[currentToolCallIndex].isStreaming = false
      }
      addMessage(content, false, 'ai-final-answer', false)
      currentToolCallIndex = -1
    } else if (type === 'error') {
      // 错误信息
      if (currentToolCallIndex >= 0 && messages.value[currentToolCallIndex]) {
        messages.value[currentToolCallIndex].isStreaming = false
      }
      addMessage(content, false, 'ai-error', false)
      currentToolCallIndex = -1
    }
    // 忽略其他类型（如 unknown），不在前端显示
  }

  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    connectionStatus.value = 'error'
    eventSource.close()

    if (currentToolCallIndex >= 0 && messages.value[currentToolCallIndex]) {
      messages.value[currentToolCallIndex].isStreaming = false
    }
  }
}

// 保存当前消息到存储
const saveCurrentMessage = (message) => {
  if (currentSessionId.value) {
    addMessageToSession(CHAT_TYPES.SUPER, currentSessionId.value, message)
    loadSessions()
  } else if (isNewChat.value) {
    // 用户在新对话中发送第一条消息时创建新会话
    const title = message.content.substring(0, 20) + (message.content.length > 20 ? '...' : '')
    const session = createSession(CHAT_TYPES.SUPER, title)
    currentSessionId.value = session.id
    isNewChat.value = false

    // 添加所有消息到新会话
    messages.value.forEach(msg => {
      addMessageToSession(CHAT_TYPES.SUPER, currentSessionId.value, msg)
    })
    saveCurrentSessionId()
    loadSessions()
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
    const session = getSession(CHAT_TYPES.SUPER, savedSessionId)
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
.super-agent-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #f9fbff;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background-color: #3f51b5;
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

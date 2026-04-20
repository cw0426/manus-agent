<template>
  <div class="chat-container">
    <!-- 聊天记录区域 -->
    <div class="chat-messages" ref="messagesContainer">
      <div v-for="(msg, index) in messages" :key="index" class="message-wrapper">
        <!-- AI消息 -->
        <div v-if="!msg.isUser"
             class="message ai-message"
             :class="[msg.type]">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="message-bubble">
            <div class="message-content markdown-body" v-html="renderStreamingMarkdown(msg.content, msg.isStreaming)"></div>
            <!-- 工具调用详情 -->
            <div v-if="msg.toolCalls && msg.toolCalls.length > 0" class="tool-calls-detail">
              <div v-for="(toolCall, tcIndex) in msg.toolCalls" :key="tcIndex" class="tool-call-item">
                <div class="tool-call-header">
                  <span class="tool-name">{{ toolCall.name }}</span>
                </div>
                <div class="tool-arguments">
                  <pre>{{ formatArguments(toolCall.arguments) }}</pre>
                </div>
                <!-- 图片展示 -->
                <div v-if="toolCall.images && toolCall.images.length > 0" class="tool-images">
                  <img
                    v-for="(imgUrl, imgIndex) in toolCall.images"
                    :key="imgIndex"
                    :src="imgUrl"
                    :alt="'图片' + (imgIndex + 1)"
                    class="tool-image"
                    @click="openImage(imgUrl)"
                    @error="handleImageError($event)"
                  />
                </div>
              </div>
            </div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
        </div>

        <!-- 用户消息 -->
        <div v-else class="message user-message" :class="[msg.type]">
          <div class="message-bubble">
            <div class="message-content">{{ msg.content }}</div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
          <div class="avatar user-avatar">
            <div class="avatar-placeholder">我</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-container">
      <div class="chat-input">
        <textarea
          v-model="inputMessage"
          @keydown.enter.prevent="sendMessage"
          placeholder="请输入消息..."
          class="input-box"
          :disabled="connectionStatus === 'connecting'"
        ></textarea>
        <button
          @click="sendMessage"
          class="send-button"
          :disabled="connectionStatus === 'connecting' || !inputMessage.trim()"
        >发送</button>
      </div>
    </div>

    <!-- 图片预览模态框 -->
    <div v-if="previewImage" class="image-preview-modal" @click="closeImagePreview">
      <img :src="previewImage" alt="预览图片" class="preview-image" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch, computed } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import AiAvatarFallback from './AiAvatarFallback.vue'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  },
  connectionStatus: {
    type: String,
    default: 'disconnected'
  },
  aiType: {
    type: String,
    default: 'default'  // 'love' 或 'super'
  }
})

const emit = defineEmits(['send-message'])

const inputMessage = ref('')
const messagesContainer = ref(null)
const previewImage = ref(null)

// 配置 markdown-it
const md = new MarkdownIt({
  html: false,
  xhtmlOut: false,
  breaks: true,
  linkify: true,
  typographer: true,
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs"><code>' +
               hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
               '</code></pre>'
      } catch (__) {}
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>'
  }
})

// 渲染 Markdown
const renderMarkdown = (content) => {
  if (!content) return ''
  return md.render(content)
}

// 预处理内容：修复 Markdown 格式问题
const preprocessMarkdown = (content) => {
  if (!content) return content
  let lines = content.split('\n')
  let processedLines = lines.map(line => {
    // 修复标题格式：###文字 -> ### 文字（Markdown 标题需要 # 后有空格）
    line = line.replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
    // 修复列表格式：1.文字 -> 1. 文字（列表需要点后有空格）
    line = line.replace(/^(\s*)(\d+\.)([^\s])/gm, '$1$2 $3')
    // 修复加粗格式：**文字：**后面紧跟非空白字符 -> **文字：** 后面加空格
    // 这样确保加粗结束后有明确的分隔
    line = line.replace(/(\*\*[^*]+\*\*)([^\s\*])/g, '$1 $2')
    return line
  })
  return processedLines.join('\n')
}

// 流式渲染 Markdown：即时渲染已完成的部分，未闭合的块暂缓渲染
const renderStreamingMarkdown = (content, isStreaming) => {
  if (!content) return isStreaming ? '<span class="typing-indicator">▋</span>' : ''
  if (!isStreaming) return md.render(preprocessMarkdown(content))

  // 预处理内容
  let processedContent = preprocessMarkdown(content)

  // 检测并处理未闭合的代码块
  const codeBlockCount = (processedContent.match(/```/g) || []).length
  let renderContent = processedContent
  let unclosedCodeBlock = false

  if (codeBlockCount % 2 !== 0) {
    // 有未闭合的代码块，临时补上闭合符号以渲染
    renderContent = processedContent + '\n```'
    unclosedCodeBlock = true
  }

  let html = md.render(renderContent)

  // 在最后添加闪烁光标
  html += '<span class="typing-indicator">▋</span>'

  return html
}

// 格式化工具参数（JSON格式化）
const formatArguments = (args) => {
  if (!args) return ''
  try {
    const parsed = JSON.parse(args)
    return JSON.stringify(parsed, null, 2)
  } catch (e) {
    return args
  }
}

// 打开图片预览
const openImage = (url) => {
  previewImage.value = url
}

// 关闭图片预览
const closeImagePreview = () => {
  previewImage.value = null
}

// 处理图片加载错误
const handleImageError = (event) => {
  event.target.style.display = 'none'
}

// 根据AI类型选择不同头像
const aiAvatar = computed(() => {
  return props.aiType === 'love'
    ? '/ai-love-avatar.png'  // 恋爱大师头像
    : '/ai-super-avatar.png' // 超级智能体头像
})

// 发送消息
const sendMessage = () => {
  if (!inputMessage.value.trim()) return

  emit('send-message', inputMessage.value)
  inputMessage.value = ''
}

// 格式化时间
const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 自动滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 监听消息变化与内容变化，自动滚动
watch(() => props.messages.length, () => {
  scrollToBottom()
})

watch(() => props.messages.map(m => m.content).join(''), () => {
  scrollToBottom()
})

onMounted(() => {
  scrollToBottom()
})
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 400px;
  background-color: #f5f5f5;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px;
  padding-bottom: 80px;
  display: flex;
  flex-direction: column;
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 72px;
}

.message-wrapper {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
}

.message {
  display: flex;
  align-items: flex-start;
  max-width: 85%;
  margin-bottom: 8px;
  min-width: 0;
}

.user-message {
  margin-left: auto;
  flex-direction: row;
}

.ai-message {
  margin-right: auto;
  max-width: calc(100% - 50px);
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-avatar {
  margin-left: 8px; /* 用户头像在右侧，左边距 */
}

.ai-avatar {
  margin-right: 8px; /* AI头像在左侧，右边距 */
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #007bff;
  color: white;
  font-weight: bold;
}

.message-bubble {
  padding: 12px;
  border-radius: 18px;
  position: relative;
  word-wrap: break-word;
  overflow-wrap: break-word;
  min-width: 100px;
  max-width: 100%;
  box-sizing: border-box;
  flex: 1;
  min-width: 0;
}

.ai-message .message-bubble {
  background-color: #e9e9eb;
  color: #333;
  border-bottom-left-radius: 4px;
  text-align: left;
}

.user-message .message-bubble {
  background-color: #007bff;
  color: white;
  border-bottom-right-radius: 4px;
  text-align: left;
}

.message-content {
  font-size: 16px;
  line-height: 1.5;
  word-wrap: break-word;
  overflow-wrap: break-word;
  max-width: 100%;
}

.message-time {
  font-size: 12px;
  opacity: 0.7;
  margin-top: 4px;
  text-align: right;
}

.chat-input-container {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: white;
  border-top: 1px solid #e0e0e0;
  z-index: 100;
  height: 72px; /* 固定高度 */
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
}

.chat-input {
  display: flex;
  padding: 16px;
  height: 100%;
  box-sizing: border-box;
  align-items: center;
}

.input-box {
  flex-grow: 1;
  border: 1px solid #ddd;
  border-radius: 20px;
  padding: 10px 16px;
  font-size: 16px;
  resize: none;
  min-height: 20px;
  max-height: 40px; /* 限制高度 */
  outline: none;
  transition: border-color 0.3s;
  overflow-y: auto;
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE & Edge */
}

/* 隐藏Webkit浏览器的滚动条 */
.input-box::-webkit-scrollbar {
  display: none;
}

.input-box:focus {
  border-color: #007bff;
}

.send-button {
  margin-left: 12px;
  background-color: #007bff;
  color: white;
  border: none;
  border-radius: 20px;
  padding: 0 20px;
  font-size: 16px;
  cursor: pointer;
  transition: background-color 0.3s;
  height: 40px;
  align-self: center;
}

.send-button:hover:not(:disabled) {
  background-color: #0069d9;
}

.typing-indicator {
  display: inline-block;
  animation: blink 0.7s infinite;
  margin-left: 2px;
}

@keyframes blink {
  0% { opacity: 0; }
  50% { opacity: 1; }
  100% { opacity: 0; }
}

.input-box:disabled, .send-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .message {
    max-width: 95%;
  }
  
  .message-content {
    font-size: 15px;
  }
  
  .chat-input {
    padding: 12px;
  }
  
  .input-box {
    padding: 8px 12px;
  }
  
  .send-button {
    padding: 0 15px;
    font-size: 14px;
  }
}

@media (max-width: 480px) {
  .avatar {
    width: 32px;
    height: 32px;
  }
  
  .message-bubble {
    padding: 10px;
  }
  
  .message-content {
    font-size: 14px;
  }
  
  .chat-input-container {
    height: 64px;
  }
  
  .chat-messages {
    bottom: 64px;
  }
}

/* 新增：不同类型消息的样式 */
.ai-tool-call .message-bubble {
  background-color: #f0f4ff !important;
  border-left: 3px solid #5c7cfa;
  font-size: 14px;
  color: #495057;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.ai-final-answer {
  animation: fadeIn 0.3s ease-in-out;
}

.ai-answer {
  animation: fadeIn 0.3s ease-in-out;
}

.ai-final {
  /* 最终回答，可以有不同的样式，例如边框高亮等 */
}

.ai-error {
  opacity: 0.7;
}

.user-question {
  /* 用户提问的特殊样式 */
}

/* 连续消息气泡样式 */
.ai-message + .ai-message {
  margin-top: 4px;
}

.ai-message + .ai-message .avatar {
  visibility: hidden;
}

.ai-message + .ai-message .message-bubble {
  border-top-left-radius: 10px;
}

/* 工具调用详情样式 */
.tool-calls-detail {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #d0d7de;
}

.tool-call-item {
  background-color: #f6f8fa;
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
}

.tool-call-item:last-child {
  margin-bottom: 0;
}

.tool-call-header {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
}

.tool-name {
  font-weight: 600;
  color: #0969da;
  font-size: 13px;
  background-color: #ddf4ff;
  padding: 2px 8px;
  border-radius: 4px;
}

.tool-arguments {
  background-color: #f0f0f0;
  border-radius: 4px;
  padding: 6px 8px;
  overflow-x: auto;
}

.tool-arguments pre {
  margin: 0;
  font-size: 12px;
  color: #333;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 工具返回的图片样式 */
.tool-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.tool-image {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: 8px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  border: 1px solid #e0e0e0;
}

.tool-image:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* 图片预览模态框 */
.image-preview-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  cursor: pointer;
}

.preview-image {
  max-width: 90%;
  max-height: 90%;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5);
}
</style>

<!-- Markdown样式需要非scoped才能应用到v-html动态内容 -->
<style>
.markdown-body {
  line-height: 1.6;
  word-wrap: break-word;
  overflow-wrap: break-word;
  max-width: 100%;
  overflow: hidden;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4,
.markdown-body h5,
.markdown-body h6 {
  margin-top: 16px;
  margin-bottom: 8px;
  font-weight: 600;
  line-height: 1.25;
}

.markdown-body h1:first-child,
.markdown-body h2:first-child,
.markdown-body h3:first-child,
.markdown-body h4:first-child {
  margin-top: 0;
}

.markdown-body h1 { font-size: 1.4em; }
.markdown-body h2 { font-size: 1.3em; }
.markdown-body h3 { font-size: 1.2em; }
.markdown-body h4 { font-size: 1.1em; }

.markdown-body p {
  margin: 0 0 12px;
  word-wrap: break-word;
  overflow-wrap: break-word;
}

.markdown-body p:last-child {
  margin-bottom: 0;
}

/* 列表样式 */
.markdown-body ul,
.markdown-body ol {
  margin: 0 0 12px;
  padding-left: 20px;
}

.markdown-body ul {
  list-style-type: disc;
  list-style-position: inside;
}

.markdown-body ol {
  list-style-type: decimal;
  list-style-position: inside;
}

.markdown-body li {
  margin: 4px 0;
  word-wrap: break-word;
  overflow-wrap: break-word;
}

.markdown-body li p {
  margin: 0;
}

/* 嵌套列表 */
.markdown-body ul ul,
.markdown-body ol ol,
.markdown-body ul ol,
.markdown-body ol ul {
  margin: 4px 0;
  padding-left: 16px;
}

/* 行内代码 */
.markdown-body code {
  background-color: rgba(0, 0, 0, 0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 0.9em;
  word-wrap: break-word;
}

/* 代码块 */
.markdown-body pre {
  background-color: #282c34;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
  max-width: 100%;
  box-sizing: border-box;
}

.markdown-body pre code {
  background-color: transparent;
  padding: 0;
  font-size: 0.85em;
  line-height: 1.5;
  color: #abb2bf;
  white-space: pre-wrap;
  word-wrap: break-word;
}

/* 代码高亮样式 */
.hljs {
  background: #282c34;
  color: #abb2bf;
  padding: 0;
  margin: 0;
}

.hljs-comment,
.hljs-quote {
  color: #5c6370;
  font-style: italic;
}

.hljs-doctag,
.hljs-keyword,
.hljs-formula {
  color: #c678dd;
}

.hljs-section,
.hljs-name,
.hljs-selector-tag,
.hljs-deletion,
.hljs-subst {
  color: #e06c75;
}

.hljs-literal {
  color: #56b6c2;
}

.hljs-string,
.hljs-regexp,
.hljs-addition,
.hljs-attribute,
.hljs-meta .hljs-string {
  color: #98c379;
}

.hljs-attr,
.hljs-variable,
.hljs-template-variable,
.hljs-type,
.hljs-selector-class,
.hljs-selector-attr,
.hljs-selector-pseudo,
.hljs-number {
  color: #d19a66;
}

.hljs-symbol,
.hljs-bullet,
.hljs-link,
.hljs-meta,
.hljs-selector-id,
.hljs-title {
  color: #61aeee;
}

.hljs-built-in,
.hljs-title.class_,
.hljs-class .hljs-title {
  color: #e6c07b;
}

.hljs-emphasis {
  font-style: italic;
}

.hljs-strong {
  font-weight: bold;
}

.hljs-link {
  text-decoration: underline;
}

/* 引用块 */
.markdown-body blockquote {
  margin: 8px 0;
  padding: 8px 12px;
  border-left: 4px solid #dfe2e5;
  background-color: #f6f8fa;
  color: #636e72;
  overflow: hidden;
}

.markdown-body blockquote p {
  margin: 0;
}

/* 加粗和斜体 */
.markdown-body strong {
  font-weight: 700;
}

.markdown-body em {
  font-style: italic;
}

/* 链接 */
.markdown-body a {
  color: #0366d6;
  text-decoration: none;
  word-wrap: break-word;
}

.markdown-body a:hover {
  text-decoration: underline;
}

/* 分隔线 */
.markdown-body hr {
  border: none;
  border-top: 1px solid #e1e4e8;
  margin: 16px 0;
}

/* 表格 */
.markdown-body table {
  border-collapse: collapse;
  margin: 8px 0;
  width: 100%;
  display: block;
  overflow-x: auto;
}

.markdown-body th,
.markdown-body td {
  border: 1px solid #dfe2e5;
  padding: 6px 12px;
}

.markdown-body th {
  background-color: #f6f8fa;
  font-weight: 600;
}

/* 图片 */
.markdown-body img {
  max-width: 100%;
  height: auto;
}

/* 删除线 */
.markdown-body del {
  text-decoration: line-through;
}

/* 任务列表 */
.markdown-body input[type="checkbox"] {
  margin-right: 6px;
}

/* 脚注 */
.markdown-body .footnotes {
  margin-top: 20px;
  font-size: 0.9em;
}
</style> 
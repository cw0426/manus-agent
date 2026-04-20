<template>
  <div class="chat-history-panel">
    <div class="panel-header">
      <h3>历史对话</h3>
      <button class="new-chat-btn" @click="$emit('new-chat')">
        + 新对话
      </button>
    </div>

    <div class="sessions-list">
      <!-- 新对话占位项 -->
      <div v-if="!currentSessionId" class="session-item new-chat-placeholder active">
        <div class="session-info">
          <div class="session-title">新对话</div>
          <div class="session-meta">
            <span class="session-count">开始新的聊天</span>
          </div>
        </div>
      </div>

      <div v-if="sessions.length === 0 && currentSessionId" class="empty-state">
        <div class="empty-icon">💬</div>
        <div>暂无历史对话</div>
      </div>
      <div
        v-for="session in sessions"
        :key="session.id"
        class="session-item"
        :class="{ active: session.id === currentSessionId }"
        @click="$emit('select', session.id)"
      >
        <div class="session-info">
          <div class="session-title">{{ session.title }}</div>
          <div class="session-meta">
            <span class="session-count">{{ session.messages.length }} 条消息</span>
            <span class="session-time">{{ formatDateTime(session.updatedAt) }}</span>
          </div>
        </div>
        <button class="delete-btn" @click.stop="handleDelete(session.id)" title="删除">
          <span>×</span>
        </button>
      </div>
    </div>

    <div class="panel-footer" v-if="sessions.length > 0">
      <button class="clear-btn" @click="handleClearAll">
        清空全部历史
      </button>
    </div>
  </div>
</template>

<script setup>
import { formatDateTime } from '../utils/chatStorage'

defineProps({
  sessions: {
    type: Array,
    default: () => []
  },
  currentSessionId: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['new-chat', 'select', 'delete', 'clear'])

const handleDelete = (sessionId) => {
  if (confirm('确定要删除这个对话吗？')) {
    emit('delete', sessionId)
  }
}

const handleClearAll = () => {
  if (confirm('确定要清空所有历史对话吗？')) {
    emit('clear')
  }
}
</script>

<style scoped>
.chat-history-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: #fff;
  border-right: 1px solid #e8e8e8;
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.new-chat-btn {
  padding: 6px 12px;
  background-color: #1890ff;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.new-chat-btn:hover {
  background-color: #40a9ff;
}

.sessions-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.empty-state {
  text-align: center;
  color: #999;
  padding: 40px 20px;
  font-size: 14px;
}

.empty-icon {
  font-size: 40px;
  margin-bottom: 12px;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
  margin-bottom: 4px;
  position: relative;
}

.session-item:hover {
  background-color: #f5f5f5;
}

.session-item.active {
  background-color: #e6f7ff;
  border: 1px solid #91d5ff;
}

.session-item.new-chat-placeholder {
  background-color: #f0f9ff;
  border: 1px dashed #91d5ff;
}

.session-item.new-chat-placeholder .session-title {
  color: #1890ff;
}

.session-info {
  flex: 1;
  overflow: hidden;
  min-width: 0;
}

.session-title {
  font-size: 14px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}

.session-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #999;
}

.session-count {
  flex-shrink: 0;
}

.session-time {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.delete-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: none;
  font-size: 18px;
  color: #999;
  cursor: pointer;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.2s;
  flex-shrink: 0;
  margin-left: 8px;
}

.session-item:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  background-color: #ff4d4f;
  color: white;
}

.panel-footer {
  padding: 12px 16px;
  border-top: 1px solid #e8e8e8;
}

.clear-btn {
  width: 100%;
  padding: 8px;
  background-color: transparent;
  color: #999;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.clear-btn:hover {
  color: #ff4d4f;
  border-color: #ff4d4f;
}

/* 滚动条样式 */
.sessions-list::-webkit-scrollbar {
  width: 4px;
}

.sessions-list::-webkit-scrollbar-track {
  background: transparent;
}

.sessions-list::-webkit-scrollbar-thumb {
  background: #ddd;
  border-radius: 2px;
}

.sessions-list::-webkit-scrollbar-thumb:hover {
  background: #ccc;
}
</style>

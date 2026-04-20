/**
 * 聊天会话存储工具类
 * 使用 localStorage 存储聊天历史
 */

// 存储键名前缀
const STORAGE_PREFIX = 'ai_chat_'

// 会话类型
export const CHAT_TYPES = {
  LOVE: 'love',
  SUPER: 'super'
}

/**
 * 获取存储键
 * @param {string} type - 会话类型
 * @returns {string} 存储键
 */
const getStorageKey = (type) => `${STORAGE_PREFIX}${type}_sessions`

/**
 * 获取所有会话列表
 * @param {string} type - 会话类型
 * @returns {Array} 会话列表
 */
export const getSessions = (type) => {
  try {
    const key = getStorageKey(type)
    const data = localStorage.getItem(key)
    return data ? JSON.parse(data) : []
  } catch (e) {
    console.error('Failed to get sessions:', e)
    return []
  }
}

/**
 * 保存所有会话
 * @param {string} type - 会话类型
 * @param {Array} sessions - 会话列表
 */
const saveSessions = (type, sessions) => {
  try {
    const key = getStorageKey(type)
    localStorage.setItem(key, JSON.stringify(sessions))
  } catch (e) {
    console.error('Failed to save sessions:', e)
  }
}

/**
 * 生成唯一ID
 * @returns {string} 唯一ID
 */
export const generateId = () => {
  return Date.now().toString(36) + Math.random().toString(36).substring(2, 9)
}

/**
 * 创建新会话
 * @param {string} type - 会话类型
 * @param {string} title - 会话标题
 * @returns {Object} 新会话对象
 */
export const createSession = (type, title = '新对话') => {
  const sessions = getSessions(type)
  const newSession = {
    id: generateId(),
    title,
    messages: [],
    createdAt: Date.now(),
    updatedAt: Date.now()
  }
  sessions.unshift(newSession)
  saveSessions(type, sessions)
  return newSession
}

/**
 * 获取单个会话
 * @param {string} type - 会话类型
 * @param {string} sessionId - 会话ID
 * @returns {Object|null} 会话对象
 */
export const getSession = (type, sessionId) => {
  const sessions = getSessions(type)
  return sessions.find(s => s.id === sessionId) || null
}

/**
 * 更新会话
 * @param {string} type - 会话类型
 * @param {string} sessionId - 会话ID
 * @param {Object} updates - 更新内容
 */
export const updateSession = (type, sessionId, updates) => {
  const sessions = getSessions(type)
  const index = sessions.findIndex(s => s.id === sessionId)
  if (index !== -1) {
    sessions[index] = {
      ...sessions[index],
      ...updates,
      updatedAt: Date.now()
    }
    // 更新后移到最前面
    const session = sessions.splice(index, 1)[0]
    sessions.unshift(session)
    saveSessions(type, sessions)
  }
}

/**
 * 添加消息到会话
 * @param {string} type - 会话类型
 * @param {string} sessionId - 会话ID
 * @param {Object} message - 消息对象
 */
export const addMessageToSession = (type, sessionId, message) => {
  const sessions = getSessions(type)
  const index = sessions.findIndex(s => s.id === sessionId)
  if (index !== -1) {
    sessions[index].messages.push({
      ...message,
      time: message.time || Date.now()
    })
    sessions[index].updatedAt = Date.now()
    // 如果是第一条用户消息，更新标题
    if (message.isUser && sessions[index].messages.filter(m => m.isUser).length === 1) {
      sessions[index].title = message.content.substring(0, 20) + (message.content.length > 20 ? '...' : '')
    }
    // 更新后移到最前面
    const session = sessions.splice(index, 1)[0]
    sessions.unshift(session)
    saveSessions(type, sessions)
  }
}

/**
 * 删除会话
 * @param {string} type - 会话类型
 * @param {string} sessionId - 会话ID
 */
export const deleteSession = (type, sessionId) => {
  const sessions = getSessions(type)
  const filtered = sessions.filter(s => s.id !== sessionId)
  saveSessions(type, filtered)
}

/**
 * 清空所有会话
 * @param {string} type - 会话类型
 */
export const clearSessions = (type) => {
  saveSessions(type, [])
}

/**
 * 格式化日期时间
 * @param {number} timestamp - 时间戳
 * @returns {string} 格式化后的日期字符串
 */
export const formatDateTime = (timestamp) => {
  const date = new Date(timestamp)
  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()

  if (isToday) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }

  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) {
    return '昨天 ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }

  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) + ' ' +
         date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

export default {
  CHAT_TYPES,
  getSessions,
  createSession,
  getSession,
  updateSession,
  addMessageToSession,
  deleteSession,
  clearSessions,
  generateId,
  formatDateTime
}

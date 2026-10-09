import { get, post, put, del, unwrap } from '@/utils/request'
import { streamSse } from '@/utils/stream'

/** AI 对话与知识问答（docs/23 H5-H6，复用 21 号 M2/M3 后端） */

export function listConversations(params) {
  return unwrap(get('/api/v1/ai/chat/conversations', params))
}

export function createConversation(data) {
  return unwrap(post('/api/v1/ai/chat/conversations', data || {}, { idempotent: true }))
}

export function updateConversation(id, data) {
  return unwrap(put(`/api/v1/ai/chat/conversations/${id}`, data))
}

export function deleteConversation(id) {
  return unwrap(del(`/api/v1/ai/chat/conversations/${id}`))
}

export function listMessages(conversationId, params) {
  return unwrap(get(`/api/v1/ai/chat/conversations/${conversationId}/messages`, params))
}

export function listChatTemplates() {
  return unwrap(get('/api/v1/ai/chat/templates'))
}

export function stopMessage(messageId) {
  return unwrap(post(`/api/v1/ai/chat/messages/${messageId}/stop`))
}

/** 流式对话：事件 meta/delta/usage/done/error */
export function chatCompletions(body, onEvent) {
  return streamSse({ url: '/api/v1/ai/chat/completions', method: 'POST', body, onEvent })
}

/** 重新生成（保留旧版本，产出新 assistant 消息） */
export function regenerate(messageId, onEvent) {
  return streamSse({ url: `/api/v1/ai/chat/messages/${messageId}/regenerate`, method: 'POST', body: {}, onEvent })
}

/** 知识问答（引用溯源）：事件 meta/delta/usage/done/error */
export function askQa(body, onEvent) {
  return streamSse({ url: '/api/v1/ai/qa/ask', method: 'POST', body, onEvent })
}

export function qaHistory(params) {
  return unwrap(get('/api/v1/ai/qa/history', params))
}

export function listModels() {
  return unwrap(get('/api/v1/ai/models/enabled'))
}

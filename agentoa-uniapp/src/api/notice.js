import { get, put, del, unwrap } from '@/utils/request'

/** 公告列表（docs/05 7.1）：普通员工只看受众包含自己的公告 */
export function listNotice(params) {
  return unwrap(get('/api/v1/notice/list', params))
}

export function getNotice(id) {
  return unwrap(get(`/api/v1/notice/${id}`))
}

/** 已读（幂等，docs/15） */
export function readNotice(id) {
  return unwrap(put(`/api/v1/notice/${id}/read`, {}))
}

// ------------------------------------------------------------- 消息中心（docs/05 7.2）

export function listMessage(params) {
  return unwrap(get('/api/v1/messages', params))
}

/** 未读统计：total / todo / notice / mention / system 分桶 */
export function unreadCount() {
  return unwrap(get('/api/v1/messages/unread-count'))
}

export function readMessage(id) {
  return unwrap(put(`/api/v1/messages/${id}/read`, {}))
}

export function readAllMessage() {
  return unwrap(put('/api/v1/messages/read-all', {}))
}

export function removeMessage(id) {
  return unwrap(del(`/api/v1/messages/${id}`))
}

/** 消息类型 -> 展示名（docs/15 nc_msg_type） */
export const MSG_TYPE_NAME = {
  TODO: '待办',
  NOTICE: '公告',
  MENTION: '提及',
  SYSTEM: '系统'
}

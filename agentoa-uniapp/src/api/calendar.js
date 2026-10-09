import { get, post, put, unwrap } from '@/utils/request'

/** 日程（docs/23 H5-H4-02，复用 /api/v1/calendar/events） */
export function listEvents(params) {
  return unwrap(get('/api/v1/calendar/events', params))
}

export function getEvent(id) {
  return unwrap(get(`/api/v1/calendar/events/${id}`))
}

export function createEvent(data) {
  return unwrap(post('/api/v1/calendar/events', data, { idempotent: true }))
}

export function acceptEvent(id) {
  return unwrap(put(`/api/v1/calendar/events/${id}/accept`))
}

export function rejectEvent(id) {
  return unwrap(put(`/api/v1/calendar/events/${id}/reject`))
}

export const EVENT_VISIBILITY = { 1: '私有', 2: '参与人可见', 3: '部门可见', 4: '全员可见' }

import { get, put, unwrap } from '@/utils/request'

/** 协同任务（docs/23 H5-H4-04，复用 /api/v1/tasks） */
export function listTasks(params) {
  return unwrap(get('/api/v1/tasks', params))
}

export function getTask(id) {
  return unwrap(get(`/api/v1/tasks/${id}`))
}

/** 状态流转：status 1待办 2进行中 3受阻 4完成 5取消 */
export function changeTaskStatus(id, data) {
  return unwrap(put(`/api/v1/tasks/${id}/status`, data, { idempotent: true }))
}

export const TASK_STATUS = {
  1: { label: '待办', tone: 'primary' },
  2: { label: '进行中', tone: 'warning' },
  3: { label: '受阻', tone: 'danger' },
  4: { label: '已完成', tone: 'success' },
  5: { label: '已取消', tone: 'default' }
}

export function taskStatus(status) {
  return TASK_STATUS[status] || { label: String(status || '-'), tone: 'default' }
}

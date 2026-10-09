import { get, put, unwrap } from '@/utils/request'

/**
 * 本人人事档案（docs/11 移动端范围）。
 * `hr:employee:list` 对 employee 角色已授权，数据范围（data_scope=5 仅本人）在服务端过滤，
 * 因此普通员工只会拿到自己的档案；HR/管理员可查询他人。
 */
export function listEmployees(params) {
  return unwrap(get('/api/v1/hr/employees', params))
}

export function getEmployee(employeeId) {
  return unwrap(get(`/api/v1/hr/employees/${employeeId}`))
}

export function employeeChanges(employeeId) {
  return unwrap(get(`/api/v1/hr/employees/${employeeId}/changes`))
}

/** 本人自助档案（docs/23 H5-H4-05）：含人事档案与教育/工作/合同 */
export function getSelfProfile() {
  return unwrap(get('/api/v1/hr/profile/self'))
}

/** 自助字段（手机、邮箱）白名单更新，docs/11 权限矩阵：员工仅自助字段 */
export function updateSelfProfile(data) {
  return unwrap(put('/api/v1/hr/profile/self', data))
}

/** 员工状态（docs/11） */
export const EMPLOYEE_STATUS = {
  DRAFT: { label: '草稿', tone: 'default' },
  PROBATION: { label: '试用期', tone: 'primary' },
  ACTIVE: { label: '在职', tone: 'success' },
  LEAVE_PENDING: { label: '待离职', tone: 'warning' },
  LEFT: { label: '已离职', tone: 'default' },
  DISABLED: { label: '已停用', tone: 'danger' }
}

export function employeeStatus(status) {
  return EMPLOYEE_STATUS[status] || { label: String(status || '-'), tone: 'default' }
}

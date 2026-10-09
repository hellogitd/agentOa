import { get, post, put, del, unwrap } from '@/utils/request'
import { newIdempotencyKey } from '@/utils/idempotency'

const IDEM = () => ({ 'Idempotency-Key': newIdempotencyKey() })

// ------------------------------------------------------------- 分类与定义（docs/05 4.1/4.2）

export function listCategories() {
  return unwrap(get('/api/v1/wf/categories'))
}

export function listDefinitions() {
  return unwrap(get('/api/v1/wf/definitions'))
}

/** 发起申请目录（登录即可）：启用分类 + 已发布流程定义（含表单快照），后台启停/发布/分类调整即时同步。 */
export function listLaunchable() {
  return unwrap(get('/api/v1/wf/launchable'))
}

/** 单个可发起流程（登录即可）：元数据 + 已发布表单快照，供发起页按 definitionId 加载。 */
export function getLaunchableDefinition(definitionId) {
  return unwrap(get(`/api/v1/wf/launchable/${definitionId}`))
}

export function getForm(formKey) {
  return unwrap(get(`/api/v1/wf/forms/${formKey}`))
}

// ------------------------------------------------------------- 流程实例（docs/05 4.3）

export function startInstance(data) {
  return unwrap(post('/api/v1/wf/instances/start', data, { header: IDEM() }))
}

export function listInstances(params) {
  return unwrap(get('/api/v1/wf/instances', params))
}

export function getInstance(instanceId) {
  return unwrap(get(`/api/v1/wf/instances/${instanceId}`))
}

export function getInstanceHistory(instanceId) {
  return unwrap(get(`/api/v1/wf/instances/${instanceId}/history`))
}

export function revokeInstance(instanceId, lockVersion) {
  return unwrap(put(`/api/v1/wf/instances/${instanceId}/revoke`, { lockVersion }, { header: IDEM() }))
}

/** 流程图 PNG 地址（需带 token，仅 H5 直接展示；小程序端不启用）。 */
export function instanceDiagramUrl(instanceId) {
  return `/api/v1/wf/instances/${instanceId}/diagram`
}

// ------------------------------------------------------------- 任务（docs/05 4.4）

export function listTodo(params) {
  return unwrap(get('/api/v1/wf/tasks/todo', params))
}

export function listDone(params) {
  return unwrap(get('/api/v1/wf/tasks/done', params))
}

export function completeTask(taskId, data) {
  return unwrap(post(`/api/v1/wf/tasks/${taskId}/complete`, data, { header: IDEM() }))
}

export function transferTask(taskId, data) {
  return unwrap(post(`/api/v1/wf/tasks/${taskId}/transfer`, data, { header: IDEM() }))
}

// ------------------------------------------------------------- 选人目录（docs/05 4.6）
/** 可选办理人检索（登录即可）：keyword 按账号名/昵称模糊匹配，返回 {records,total,pageNum,pageSize,pages} */
export function listAssignableUsers(params) {
  return unwrap(get('/api/v1/wf/assignable-users', params))
}

// ------------------------------------------------------------- 业务承接单（docs/12 暂缓项交付）

function businessGroup(base, { canDelete = false } = {}) {
  return {
    base,
    create(data) {
      return unwrap(post(base, data, { header: IDEM() }))
    },
    update(id, data) {
      return unwrap(put(`${base}/${id}`, data))
    },
    get(id) {
      return unwrap(get(`${base}/${id}`))
    },
    list(params) {
      return unwrap(get(base, params))
    },
    remove(id) {
      if (!canDelete) return Promise.reject(new Error(`${base} 不支持删除`))
      return unwrap(del(`${base}/${id}`))
    },
    submit(id, lockVersion, assigneeSelections) {
      const body = { lockVersion }
      if (assigneeSelections && Object.keys(assigneeSelections).length) body.assigneeSelections = assigneeSelections
      return unwrap(post(`${base}/${id}/submit`, body, { header: IDEM() }))
    }
  }
}

export const leaveApi = businessGroup('/api/v1/attendance/leaves')
export const overtimeApi = businessGroup('/api/v1/attendance/overtimes')
export const correctionApi = businessGroup('/api/v1/attendance/corrections')
export const reimburseApi = businessGroup('/api/v1/finance/reimburses', { canDelete: true })
export const lifecycleApi = businessGroup('/api/v1/hr/lifecycle-requests')

export function businessApiFor(businessType) {
  switch (String(businessType || '').toLowerCase()) {
    case 'leave':
      return leaveApi
    case 'overtime':
      return overtimeApi
    case 'correction':
      return correctionApi
    case 'reimburse':
      return reimburseApi
    case 'regularize':
    case 'offboard':
      return lifecycleApi
    default:
      return null
  }
}

// ------------------------------------------------------------- 通用申请（纯 OA 表单/自定义流程）

/** 通用申请草稿（未绑定业务表，formData 为表单 JSON 字符串）。 */
export function createGenericRequest(data) {
  return unwrap(post('/api/v1/wf/generic-requests', data, { header: IDEM() }))
}

/** 通用申请直接发起：建单 + 发起流程一并完成。 */
export function launchGenericRequest(data) {
  return unwrap(post('/api/v1/wf/generic-requests/launch', data, { header: IDEM() }))
}

/** 六个 P0 业务快捷入口（docs/12 步骤 6）：目录本体取自 /api/v1/wf/launchable，这里只保留快捷排序、图标与业务承接 API 映射。 */
export const APPLY_TYPES = [
  {
    type: 'leave',
    name: '请假',
    base: '/api/v1/attendance/leaves',
    icon: '休',
    desc: '请假申请（额度由服务端核算）'
  },
  {
    type: 'overtime',
    name: '加班',
    base: '/api/v1/attendance/overtimes',
    icon: '勤',
    desc: '加班申请'
  },
  {
    type: 'correction',
    name: '补卡',
    base: '/api/v1/attendance/corrections',
    icon: '卡',
    desc: '补卡申请（每月上限 3 次）'
  },
  {
    type: 'reimburse',
    name: '报销',
    base: '/api/v1/finance/reimburses',
    icon: '费',
    desc: '报销申请（明细与发票）'
  },
  {
    type: 'regularize',
    name: '转正',
    base: '/api/v1/hr/lifecycle-requests',
    icon: '转',
    desc: '员工转正申请'
  },
  {
    type: 'offboard',
    name: '离职',
    base: '/api/v1/hr/lifecycle-requests',
    icon: '离',
    desc: '员工离职申请'
  }
]

export const BUSINESS_TYPE_NAME = {
  leave: '请假',
  overtime: '加班',
  correction: '补卡',
  reimburse: '报销',
  regularize: '转正',
  offboard: '离职'
}

/** oa_flow_instance.status / InstanceVo.status（docs/12 FlowInstanceStatus） */
export const INSTANCE_STATUS = {
  1: { label: '审批中', tone: 'primary' },
  2: { label: '已通过', tone: 'success' },
  3: { label: '已拒绝', tone: 'danger' },
  4: { label: '已撤销', tone: 'default' },
  5: { label: '已挂起', tone: 'warning' },
  6: { label: '已终止', tone: 'default' }
}

/** 业务承接单状态（docs/12 BizRequestStatus，报销含 5待付款/6已付款） */
export const BIZ_STATUS = {
  1: { label: '草稿', tone: 'default' },
  2: { label: '审批中', tone: 'primary' },
  3: { label: '已通过', tone: 'success' },
  4: { label: '已拒绝', tone: 'danger' },
  5: { label: '待付款', tone: 'warning' },
  6: { label: '已付款', tone: 'success' },
  7: { label: '已撤销', tone: 'default' }
}

export function instanceStatus(status) {
  return INSTANCE_STATUS[status] || { label: `状态 ${status}`, tone: 'default' }
}

export function bizStatus(status) {
  return BIZ_STATUS[status] || { label: `状态 ${status}`, tone: 'default' }
}

import { get, post, unwrap } from '@/utils/request'
import { newIdempotencyKey } from '@/utils/idempotency'

const IDEM = (key) => ({ 'Idempotency-Key': key || newIdempotencyKey() })

/** 打卡（docs/05 5.2）：服务器接收时间为事实，幂等键必填；重试复用同一幂等键防重复打卡。 */
export function punch(data, idempotencyKey) {
  return unwrap(post('/api/v1/attendance/punch', data, { header: IDEM(idempotencyKey) }))
}

export function punchToday() {
  return unwrap(get('/api/v1/attendance/punch/today'))
}

export function punchRecords(params) {
  return unwrap(get('/api/v1/attendance/punch/records', params))
}

export function dayToday() {
  return unwrap(get('/api/v1/attendance/days/today'))
}

export function myBalance(year) {
  return unwrap(get('/api/v1/attendance/leaves/balance', { year }))
}

export function myLedger(params) {
  return unwrap(get('/api/v1/attendance/leaves/ledger', params))
}

/** 打卡类型（docs/05 5.2）：1 上班 / 2 下班 */
export const PUNCH_TYPE = {
  1: '上班打卡',
  2: '下班打卡',
  3: '外勤打卡',
  4: '补卡'
}

/** 请假假种（docs/13 模板白名单） */
export const LEAVE_TYPES = [
  { value: 'annual', label: '年假' },
  { value: 'compensatory', label: '调休' },
  { value: 'sick', label: '病假' },
  { value: 'personal', label: '事假' },
  { value: 'marriage', label: '婚假' },
  { value: 'maternity', label: '产假' },
  { value: 'paternity', label: '陪产假' },
  { value: 'bereavement', label: '丧假' }
]

export const OVERTIME_TYPES = [
  { value: 'weekday', label: '工作日加班' },
  { value: 'weekend', label: '休息日加班' },
  { value: 'holiday', label: '节假日加班' }
]

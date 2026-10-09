import { AxiosPromise } from 'axios';
import request from '@/utils/request';

/** /api/v1 统一响应封装 */
export interface ApiEnvelope<T = any> {
  code: number;
  msg: string;
  data: T;
  timestamp: number;
  requestId: string;
}

/** 分页响应体 */
export interface PageVo<T = any> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  pages: number;
}

export interface ShiftVO {
  id: string;
  shiftCode: string;
  shiftName: string;
  workStartTime: string;
  workEndTime: string;
  restStartTime?: string;
  restEndTime?: string;
  isCrossDay: number;
  flexibleMinutes: number;
  graceMinutes: number;
  punchWindowStart: number;
  punchWindowEnd: number;
  status: string;
  remark?: string;
}

export interface ShiftForm {
  id?: string;
  shiftCode: string;
  shiftName: string;
  workStartTime: string;
  workEndTime: string;
  restStartTime?: string;
  restEndTime?: string;
  isCrossDay: number;
  flexibleMinutes: number;
  graceMinutes: number;
  punchWindowStart: number;
  punchWindowEnd: number;
  status: string;
  remark?: string;
}

export interface AttendanceGroupVO {
  id: string;
  groupCode: string;
  groupName: string;
  shiftId: string;
  shiftName?: string;
  workDays: string;
  effectiveDate: string;
  status: string;
  memberCount?: number;
  remark?: string;
}

export interface AttendanceGroupForm {
  id?: string;
  groupCode: string;
  groupName: string;
  shiftId: string;
  workDays: string;
  effectiveDate: string;
  status: string;
  remark?: string;
}

export interface AttendanceMemberVO {
  id: string;
  groupId: string;
  userId: string;
  nickname?: string;
  employeeId?: string;
  validFrom: string;
  validTo?: string;
}

export interface AttendanceMemberForm {
  userId: number;
  employeeId?: string;
  validFrom: string;
  validTo?: string;
}

export interface CalendarVO {
  id: string;
  workDate: string;
  dayType: string;
  description?: string;
  ruleVersion?: number;
}

export interface CalendarForm {
  workDate: string;
  dayType: string;
  description?: string;
}

export interface HolidayVO {
  id: string;
  holidayDate: string;
  holidayName: string;
  holidayType: string;
  year?: number;
}

export interface HolidayForm {
  holidayDate: string;
  holidayName: string;
  holidayType: string;
}

export interface PunchForm {
  punchType: 1 | 2 | 3;
  lng?: number;
  lat?: number;
  accuracyMeters?: number;
  address?: string;
  device?: string;
  wifiName?: string;
  photoFileId?: string;
  source?: number;
}

export interface PunchVO {
  punchTime: string;
  punchType: number;
  isLate?: boolean;
  lateMinutes?: number;
  isEarly?: boolean;
  earlyMinutes?: number;
  location?: string;
}

export interface PunchRecordVO {
  id: string;
  userId: string;
  punchDate: string;
  punchTime: string;
  punchType: number;
  isLate?: number;
  lateMinutes?: number;
  isEarly?: number;
  earlyMinutes?: number;
  address?: string;
  device?: string;
  ip?: string;
  source?: number;
}

export interface PunchTodayVO {
  attendanceDate?: string;
  todayPunched?: boolean;
  punchInTime?: string;
  punchOutTime?: string;
  workStatus?: number;
  scheduledMinutes?: number;
  workedMinutes?: number;
  lateMinutes?: number;
  earlyMinutes?: number;
  leaveMinutes?: number;
  isAbnormal?: number;
  abnormalReason?: string;
}

export interface BalanceVO {
  id: string;
  year: number;
  leaveType: string;
  typeName?: string;
  quotaLimited?: boolean;
  totalMinutes?: number;
  frozenMinutes?: number;
  usedMinutes?: number;
  availableMinutes?: number;
  expireDate?: string;
}

export interface BalanceGrantForm {
  userId: number;
  employeeId?: string;
  year: number;
  leaveType: string;
  minutes: number;
  validFrom?: string;
  expireDate?: string;
}

export interface BatchVO {
  id: string;
  batchNo: string;
  year: number;
  leaveType: string;
  grantMinutes?: number;
  frozenMinutes?: number;
  usedMinutes?: number;
  expiredMinutes?: number;
  availableMinutes?: number;
  validFrom?: string;
  expireDate?: string;
  status: number;
}

export interface LedgerVO {
  id: string;
  balanceId: string;
  eventKey?: string;
  businessType?: string;
  businessId?: string;
  submissionNo?: number;
  action: string;
  totalDelta?: number;
  frozenDelta?: number;
  usedDelta?: number;
  leaveMinutes?: number;
  operatorId?: string;
  operatorName?: string;
  createTime?: string;
}

export interface AttendanceDayVO {
  id: string;
  userId: string;
  nickname?: string;
  deptId?: string;
  attendanceDate: string;
  firstPunchTime?: string;
  lastPunchTime?: string;
  workStatus?: number;
  missingPunch?: number;
  scheduledMinutes?: number;
  workedMinutes?: number;
  lateMinutes?: number;
  earlyMinutes?: number;
  leaveMinutes?: number;
  overtimeMinutes?: number;
  isAbnormal?: number;
  abnormalReason?: string;
}

export interface MonthlyReportVO {
  yearMonth: string;
  userId: string;
  nickname?: string;
  deptId?: string;
  attendanceDays?: number;
  lateCount?: number;
  earlyCount?: number;
  absentCount?: number;
  leaveMinutes?: number;
  overtimeMinutes?: number;
  abnormalCount?: number;
}

function newIdempotencyKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `idem-${Date.now()}-${Math.random()}`;
}

// ------------------------------------------------------------- 班次

export function listShift(query: any): AxiosPromise<ApiEnvelope<PageVo<ShiftVO>>> {
  return request({ url: '/api/v1/attendance/shifts', method: 'get', params: query });
}

export function listAllShift(): AxiosPromise<ApiEnvelope<ShiftVO[]>> {
  return request({ url: '/api/v1/attendance/shifts/all', method: 'get' });
}

export function getShift(id: string): AxiosPromise<ApiEnvelope<ShiftVO>> {
  return request({ url: `/api/v1/attendance/shifts/${id}`, method: 'get' });
}

export function addShift(data: ShiftForm) {
  return request({ url: '/api/v1/attendance/shifts', method: 'post', data });
}

export function updateShift(id: string, data: ShiftForm) {
  return request({ url: `/api/v1/attendance/shifts/${id}`, method: 'put', data });
}

export function delShift(id: string) {
  return request({ url: `/api/v1/attendance/shifts/${id}`, method: 'delete' });
}

// ------------------------------------------------------------- 考勤组

export function listGroup(query: any): AxiosPromise<ApiEnvelope<PageVo<AttendanceGroupVO>>> {
  return request({ url: '/api/v1/attendance/groups', method: 'get', params: query });
}

export function listAllGroup(): AxiosPromise<ApiEnvelope<AttendanceGroupVO[]>> {
  return request({ url: '/api/v1/attendance/groups/all', method: 'get' });
}

export function getGroup(id: string): AxiosPromise<ApiEnvelope<AttendanceGroupVO>> {
  return request({ url: `/api/v1/attendance/groups/${id}`, method: 'get' });
}

export function addGroup(data: AttendanceGroupForm) {
  return request({ url: '/api/v1/attendance/groups', method: 'post', data });
}

export function updateGroup(id: string, data: AttendanceGroupForm) {
  return request({ url: `/api/v1/attendance/groups/${id}`, method: 'put', data });
}

export function delGroup(id: string) {
  return request({ url: `/api/v1/attendance/groups/${id}`, method: 'delete' });
}

export function listGroupMember(id: string, query: any): AxiosPromise<ApiEnvelope<PageVo<AttendanceMemberVO>>> {
  return request({ url: `/api/v1/attendance/groups/${id}/members`, method: 'get', params: query });
}

export function addGroupMember(id: string, data: AttendanceMemberForm) {
  return request({ url: `/api/v1/attendance/groups/${id}/members`, method: 'post', data });
}

export function delGroupMember(id: string, memberId: string) {
  return request({ url: `/api/v1/attendance/groups/${id}/members/${memberId}`, method: 'delete' });
}

// ------------------------------------------------------------- 工作日历

export function listCalendar(params: { year: number; month?: number }): AxiosPromise<ApiEnvelope<CalendarVO[]>> {
  return request({ url: '/api/v1/attendance/calendar', method: 'get', params });
}

export function saveCalendar(data: CalendarForm[]): AxiosPromise<ApiEnvelope<CalendarVO[]>> {
  return request({ url: '/api/v1/attendance/calendar', method: 'put', data });
}

export function listHoliday(year?: number): AxiosPromise<ApiEnvelope<HolidayVO[]>> {
  return request({ url: '/api/v1/attendance/calendar/holidays', method: 'get', params: { year } });
}

export function addHoliday(data: HolidayForm) {
  return request({ url: '/api/v1/attendance/calendar/holidays', method: 'post', data });
}

export function delHoliday(id: string) {
  return request({ url: `/api/v1/attendance/calendar/holidays/${id}`, method: 'delete' });
}

// ------------------------------------------------------------- 打卡

export function punch(data: PunchForm): AxiosPromise<ApiEnvelope<PunchVO>> {
  return request({
    url: '/api/v1/attendance/punch',
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

export function punchToday(): AxiosPromise<ApiEnvelope<PunchTodayVO>> {
  return request({ url: '/api/v1/attendance/punch/today', method: 'get' });
}

export function listPunchRecord(query: any): AxiosPromise<ApiEnvelope<PageVo<PunchRecordVO>>> {
  return request({ url: '/api/v1/attendance/punch/records', method: 'get', params: query });
}

// ------------------------------------------------------------- 假期余额

export function listMyBalance(year?: number): AxiosPromise<ApiEnvelope<BalanceVO[]>> {
  return request({ url: '/api/v1/attendance/leaves/balance', method: 'get', params: { year } });
}

export function listUserBalance(userId: string, year?: number): AxiosPromise<ApiEnvelope<BalanceVO[]>> {
  return request({ url: `/api/v1/attendance/leaves/balance/${userId}`, method: 'get', params: { year } });
}

export function grantBalance(data: BalanceGrantForm): AxiosPromise<ApiEnvelope<BalanceVO>> {
  return request({
    url: '/api/v1/attendance/leaves/balance/grant',
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

export function listLedger(query: any): AxiosPromise<ApiEnvelope<PageVo<LedgerVO>>> {
  return request({ url: '/api/v1/attendance/leaves/ledger', method: 'get', params: query });
}

export function listBatches(params: { userId?: string; year?: number; leaveType?: string }): AxiosPromise<ApiEnvelope<BatchVO[]>> {
  return request({ url: '/api/v1/attendance/leaves/batches', method: 'get', params });
}

export function expireBatches(): AxiosPromise<ApiEnvelope<number>> {
  return request({ url: '/api/v1/attendance/leaves/expire-scan', method: 'post', data: {} });
}

// ------------------------------------------------------------- 考勤日

export function listDay(query: any): AxiosPromise<ApiEnvelope<PageVo<AttendanceDayVO>>> {
  return request({ url: '/api/v1/attendance/days', method: 'get', params: query });
}

export function todayDay(): AxiosPromise<ApiEnvelope<AttendanceDayVO>> {
  return request({ url: '/api/v1/attendance/days/today', method: 'get' });
}

// ------------------------------------------------------------- 报表

export function listDailyStatistics(query: any): AxiosPromise<ApiEnvelope<PageVo<AttendanceDayVO>>> {
  return request({ url: '/api/v1/attendance/statistics/daily', method: 'get', params: query });
}

export function listMonthlyStatistics(query: any): AxiosPromise<ApiEnvelope<MonthlyReportVO[]>> {
  return request({ url: '/api/v1/attendance/statistics/monthly', method: 'get', params: query });
}

export function exportStatistics(query: any) {
  return request({
    url: '/api/v1/attendance/statistics/export',
    method: 'get',
    params: query,
    responseType: 'blob'
  });
}

// ------------------------------------------------------------- 排班指派（P1，AT-03）

export interface ScheduleAssignmentVO {
  id: string;
  userId: string;
  userName?: string;
  shiftId: string;
  shiftName?: string;
  workDate: string;
  source: number;
  remark?: string;
}

export interface ScheduleAssignmentForm {
  userId: string;
  shiftId: string;
  workDate?: string;
  dateFrom?: string;
  dateTo?: string;
  source?: number;
  remark?: string;
}

export function listSchedule(query: { userId?: string; dateFrom?: string; dateTo?: string }): AxiosPromise<ApiEnvelope<ScheduleAssignmentVO[]>> {
  return request({ url: '/api/v1/attendance/schedules', method: 'get', params: query });
}

export function addSchedule(data: ScheduleAssignmentForm) {
  return request({ url: '/api/v1/attendance/schedules', method: 'post', data });
}

export function delSchedule(assignmentId: string) {
  return request({ url: `/api/v1/attendance/schedules/${assignmentId}`, method: 'delete' });
}

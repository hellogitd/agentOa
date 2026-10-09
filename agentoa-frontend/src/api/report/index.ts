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

/** 分页响应 */
export interface PageVo<T = any> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  pages: number;
}

function newIdempotencyKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `idem-${Date.now()}-${Math.random()}`;
}

// ------------------------------------------------------------- 指标与图表
export interface MetricVO {
  id: string;
  metricCode: string;
  metricName: string;
  category: string;
  unit: string;
  description: string;
  currentVersion: number;
  definition: string;
  status: string;
}

export interface StatCardVO {
  code: string;
  name: string;
  unit: string;
  value: string;
  raw: number;
}

export interface LabelValueVO {
  label: string;
  value: number;
  value2?: number;
}

export interface TrendPointVO {
  date: string;
  value: number;
  value2?: number;
}

export interface ExceptionItemVO {
  id: string;
  label: string;
  detail: string;
  time: string;
}

export interface HeaderVO {
  key: string;
  label: string;
}

export interface DetailTableVO {
  headers: HeaderVO[];
  rows: Record<string, any>[];
  total: number;
}

// ------------------------------------------------------------- 工作台
export interface WorkItemVO {
  id: string;
  title: string;
  businessType: string;
  status: string;
  time: string;
}

export interface WorkEventVO {
  id: string;
  title: string;
  startTime: string;
  endTime: string;
}

export interface WorkNoticeVO {
  id: string;
  title: string;
  publishTime: string;
}

export interface PunchTodayVO {
  todayPunched: boolean;
  punchInTime: string;
  punchOutTime: string;
}

export interface BalanceVO {
  leaveType: string;
  totalMinutes: number;
  frozenMinutes: number;
  usedMinutes: number;
  availableMinutes: number;
}

export interface WorkbenchManagementVO {
  deptAttendanceRate: string;
  monthReimburseAmount: string;
  avgFlowDurationHours: string;
}

export interface QuickLinkVO {
  label: string;
  url: string;
}

export interface WorkbenchVO {
  todoCount: number;
  initiatedCount: number;
  todoRecent: WorkItemVO[];
  initiatedRecent: WorkItemVO[];
  todayEvents: WorkEventVO[];
  notices: WorkNoticeVO[];
  attendance: PunchTodayVO;
  leaveBalances: BalanceVO[];
  management?: WorkbenchManagementVO;
  quickLinks: QuickLinkVO[];
}

// ------------------------------------------------------------- 看板
export interface HrStatisticsVO {
  cards: StatCardVO[];
  deptDistribution: LabelValueVO[];
  levelDistribution: LabelValueVO[];
  tenureDistribution: LabelValueVO[];
  trend12m: TrendPointVO[];
  metrics: MetricVO[];
}

export interface AttendanceStatisticsVO {
  cards: StatCardVO[];
  trend30: TrendPointVO[];
  deptLateTop: LabelValueVO[];
  leaveTypeDistribution: LabelValueVO[];
  overtimeTop: LabelValueVO[];
  exceptions: ExceptionItemVO[];
  metrics: MetricVO[];
}

export interface FinanceStatisticsVO {
  cards: StatCardVO[];
  trend12m: TrendPointVO[];
  typeDistribution: LabelValueVO[];
  deptTop: LabelValueVO[];
  metrics: MetricVO[];
}

export interface FlowStatisticsVO {
  cards: StatCardVO[];
  trend30: TrendPointVO[];
  byType: LabelValueVO[];
  topSlow: LabelValueVO[];
  timeouts: ExceptionItemVO[];
  metrics: MetricVO[];
}

// ------------------------------------------------------------- 导出
export interface ExportVO {
  id: string;
  exportNo: string;
  reportType: string;
  metricVersion: number;
  format: string;
  filters: string;
  status: string;
  rowCount: number;
  fileId: string;
  errorMessage: string;
  requestedBy: string;
  createTime: string;
  finishTime: string;
}

export interface ExportForm {
  reportType: string;
  format?: string;
  startDate?: string;
  endDate?: string;
  deptId?: number;
}

// ------------------------------------------------------------- 工作台接口
export function getWorkbench(): AxiosPromise<ApiEnvelope<WorkbenchVO>> {
  return request.get('/api/v1/report/dashboard/workbench');
}

// ------------------------------------------------------------- 看板接口
export function getHrStatistics(query: any): AxiosPromise<ApiEnvelope<HrStatisticsVO>> {
  return request.get('/api/v1/report/hr/statistics', { params: query });
}

export function getAttendanceStatistics(query: any): AxiosPromise<ApiEnvelope<AttendanceStatisticsVO>> {
  return request.get('/api/v1/report/attendance/statistics', { params: query });
}

export function getFinanceStatistics(query: any): AxiosPromise<ApiEnvelope<FinanceStatisticsVO>> {
  return request.get('/api/v1/report/finance/statistics', { params: query });
}

export function getFlowStatistics(query: any): AxiosPromise<ApiEnvelope<FlowStatisticsVO>> {
  return request.get('/api/v1/report/flow/statistics', { params: query });
}

export function listMetrics(category?: string): AxiosPromise<ApiEnvelope<MetricVO[]>> {
  return request.get('/api/v1/report/metrics', { params: { category } });
}

export function listDetails(type: string, query: any): AxiosPromise<ApiEnvelope<DetailTableVO>> {
  return request.get(`/api/v1/report/details/${type}`, { params: query });
}

// ------------------------------------------------------------- 导出接口
export function exportReport(type: string, query: any, format: string = 'XLSX'): AxiosPromise<any> {
  return request.get(`/api/v1/report/export/${type}`, {
    params: { ...query, format },
    responseType: 'blob',
    headers: { repeatSubmit: false }
  });
}

export function createExport(data: ExportForm): AxiosPromise<ApiEnvelope<ExportVO>> {
  return request.post('/api/v1/report/exports', data, {
    headers: { 'Idempotency-Key': newIdempotencyKey(), repeatSubmit: false }
  });
}

export function listExports(query: any): AxiosPromise<ApiEnvelope<PageVo<ExportVO>>> {
  return request.get('/api/v1/report/exports', { params: query });
}

export function getExport(id: string): AxiosPromise<ApiEnvelope<ExportVO>> {
  return request.get(`/api/v1/report/exports/${id}`);
}

export function downloadExportFile(fileId: string): AxiosPromise<any> {
  return request.get(`/api/v1/files/${fileId}/download`, {
    responseType: 'blob',
    headers: { repeatSubmit: false }
  });
}

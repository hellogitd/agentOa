import { AxiosPromise } from 'axios';
import request from '@/utils/request';

/** /api/v1 统一响应封装（docs/05 1.3） */
export interface ApiEnvelope<T = any> {
  code: number;
  msg: string;
  data: T;
  timestamp: number;
  requestId: string;
}

/** 分页响应体（docs/05 1.4） */
export interface PageVo<T = any> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  pages: number;
}

export interface DeptTreeNode {
  id: string;
  name: string;
  parentId: string;
  sort: number;
  status: string;
  children: DeptTreeNode[];
}

export interface DeptForm {
  deptId?: string;
  parentId: string;
  deptName: string;
  orderNum: number;
  leader?: string;
  phone?: string;
  email?: string;
  status: string;
}

export interface PostVO {
  id: string;
  positionCode: string;
  positionName: string;
  positionLevel: string;
  positionSort: number;
  status: string;
  remark: string;
}

export interface PostForm {
  id?: string;
  positionCode: string;
  positionName: string;
  positionLevel?: string;
  positionSort: number;
  status: string;
  remark?: string;
}

export interface EmployeeVO {
  id: string;
  userId?: string;
  employeeNo: string;
  name: string;
  gender: string;
  birthDate?: string;
  idCardNo?: string;
  phone?: string;
  email?: string;
  deptId: string;
  deptName?: string;
  postId?: string;
  postName?: string;
  positionLevel?: string;
  directLeaderId?: string;
  directLeaderName?: string;
  status: string;
  entryDate?: string;
  probationEndDate?: string;
  regularDate?: string;
  leaveDate?: string;
  remark?: string;
}

export interface EmployeeForm {
  id?: string;
  employeeNo?: string;
  name: string;
  gender?: string;
  birthDate?: string;
  idCardNo?: string;
  phone?: string;
  email?: string;
  deptId: string;
  postId?: string;
  positionLevel?: string;
  directLeaderId?: string;
  entryDate?: string;
  probationEndDate?: string;
  remark?: string;
}

export interface OnboardForm extends EmployeeForm {
  workflowInstanceId?: string;
}

export interface EmployeeChangeVO {
  id: string;
  eventId: string;
  eventType: string;
  fromStatus?: string;
  toStatus?: string;
  detail?: string;
  operatorName?: string;
  operateTime: string;
}

export interface ImportRowResult {
  rowNumber: number;
  employeeNo?: string;
  name?: string;
  valid: boolean;
  errors: string[];
}

export interface ImportReportVO {
  imported: boolean;
  totalRows: number;
  successRows: number;
  failedRows: number;
  rows: ImportRowResult[];
}

function newIdempotencyKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `idem-${Date.now()}-${Math.random()}`;
}

// ------------------------------------------------------------- 部门

export function deptTree(): AxiosPromise<ApiEnvelope<DeptTreeNode[]>> {
  return request({ url: '/api/v1/hr/departments/tree', method: 'get' });
}

export function getDept(deptId: string): AxiosPromise<ApiEnvelope<DeptForm>> {
  return request({ url: `/api/v1/hr/departments/${deptId}`, method: 'get' });
}

export function addDept(data: DeptForm) {
  return request({ url: '/api/v1/hr/departments', method: 'post', data });
}

export function updateDept(deptId: string, data: DeptForm) {
  return request({ url: `/api/v1/hr/departments/${deptId}`, method: 'put', data });
}

export function updateDeptStatus(deptId: string, status: string) {
  return request({ url: `/api/v1/hr/departments/${deptId}/status`, method: 'put', data: { status } });
}

export function delDept(deptId: string) {
  return request({ url: `/api/v1/hr/departments/${deptId}`, method: 'delete' });
}

// ------------------------------------------------------------- 岗位

export function listPost(query: any): AxiosPromise<ApiEnvelope<PageVo<PostVO>>> {
  return request({ url: '/api/v1/hr/posts', method: 'get', params: query });
}

export function getPost(postId: string): AxiosPromise<ApiEnvelope<PostVO>> {
  return request({ url: `/api/v1/hr/posts/${postId}`, method: 'get' });
}

export function addPost(data: PostForm) {
  return request({ url: '/api/v1/hr/posts', method: 'post', data });
}

export function updatePost(postId: string, data: PostForm) {
  return request({ url: `/api/v1/hr/posts/${postId}`, method: 'put', data });
}

export function delPost(postId: string) {
  return request({ url: `/api/v1/hr/posts/${postId}`, method: 'delete' });
}

// ------------------------------------------------------------- 员工

export function listEmployee(query: any): AxiosPromise<ApiEnvelope<PageVo<EmployeeVO>>> {
  return request({ url: '/api/v1/hr/employees', method: 'get', params: query });
}

export function getEmployee(employeeId: string): AxiosPromise<ApiEnvelope<EmployeeVO>> {
  return request({ url: `/api/v1/hr/employees/${employeeId}`, method: 'get' });
}

export function getEmployeeChanges(employeeId: string): AxiosPromise<ApiEnvelope<EmployeeChangeVO[]>> {
  return request({ url: `/api/v1/hr/employees/${employeeId}/changes`, method: 'get' });
}

export function addEmployee(data: EmployeeForm) {
  return request({
    url: '/api/v1/hr/employees',
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

export function updateEmployee(employeeId: string, data: EmployeeForm) {
  return request({ url: `/api/v1/hr/employees/${employeeId}`, method: 'put', data });
}

export function updateEmployeeProfile(employeeId: string, data: { phone?: string; email?: string }) {
  return request({ url: `/api/v1/hr/employees/${employeeId}/profile`, method: 'put', data });
}

export function delEmployee(employeeId: string) {
  return request({ url: `/api/v1/hr/employees/${employeeId}`, method: 'delete' });
}

export function onboard(data: OnboardForm) {
  return request({
    url: '/api/v1/hr/employees/onboard',
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

export function regularize(data: { employeeId: string; regularDate?: string; remark?: string }) {
  return request({
    url: '/api/v1/hr/employees/regularize',
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

export function offboard(data: { employeeId: string; reason?: string; lastWorkingDay?: string }) {
  return request({
    url: '/api/v1/hr/employees/offboard',
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

export function updateEmployeeStatus(employeeId: string, data: { status: string; reason?: string }) {
  return request({ url: `/api/v1/hr/employees/${employeeId}/status`, method: 'put', data });
}

export function importEmployees(file: File): AxiosPromise<ApiEnvelope<ImportReportVO>> {
  const form = new FormData();
  form.append('file', file);
  return request({
    url: '/api/v1/hr/employees/import',
    method: 'post',
    data: form,
    headers: { repeatSubmit: false }
  });
}

export function downloadImportTemplate() {
  return request({ url: '/api/v1/hr/employees/import/template', method: 'get', responseType: 'blob' });
}

export function exportEmployees(query: any) {
  return request({ url: '/api/v1/hr/employees/export', method: 'get', params: query, responseType: 'blob' });
}

// ------------------------------------------------------------- 教育 / 工作经历（P1）

export interface EducationVO {
  id: string;
  employeeId: string;
  school?: string;
  major?: string;
  education?: string;
  degree?: string;
  startDate?: string;
  endDate?: string;
  isFullTime?: number;
  remark?: string;
}

export interface EducationForm {
  school?: string;
  major?: string;
  education?: string;
  degree?: string;
  startDate?: string;
  endDate?: string;
  isFullTime?: number;
  remark?: string;
}

export interface WorkVO {
  id: string;
  employeeId: string;
  company?: string;
  position?: string;
  startDate?: string;
  endDate?: string;
  leaveReason?: string;
  remark?: string;
}

export interface WorkForm {
  company?: string;
  position?: string;
  startDate?: string;
  endDate?: string;
  leaveReason?: string;
  remark?: string;
}

export function listEducations(employeeId: string): AxiosPromise<ApiEnvelope<EducationVO[]>> {
  return request({ url: `/api/v1/hr/employees/${employeeId}/educations`, method: 'get' });
}

export function addEducation(employeeId: string, data: EducationForm) {
  return request({ url: `/api/v1/hr/employees/${employeeId}/educations`, method: 'post', data });
}

export function updateEducation(employeeId: string, educationId: string, data: EducationForm) {
  return request({ url: `/api/v1/hr/employees/${employeeId}/educations/${educationId}`, method: 'put', data });
}

export function delEducation(employeeId: string, educationId: string) {
  return request({ url: `/api/v1/hr/employees/${employeeId}/educations/${educationId}`, method: 'delete' });
}

export function listWorks(employeeId: string): AxiosPromise<ApiEnvelope<WorkVO[]>> {
  return request({ url: `/api/v1/hr/employees/${employeeId}/works`, method: 'get' });
}

export function addWork(employeeId: string, data: WorkForm) {
  return request({ url: `/api/v1/hr/employees/${employeeId}/works`, method: 'post', data });
}

export function updateWork(employeeId: string, workId: string, data: WorkForm) {
  return request({ url: `/api/v1/hr/employees/${employeeId}/works/${workId}`, method: 'put', data });
}

export function delWork(employeeId: string, workId: string) {
  return request({ url: `/api/v1/hr/employees/${employeeId}/works/${workId}`, method: 'delete' });
}

// ------------------------------------------------------------- 员工异动（P1，API 规范 3.6）

export interface ChangeVO {
  id: string;
  employeeId: string;
  employeeName?: string;
  changeType: number;
  effectiveDate: string;
  oldDeptId?: string;
  newDeptId?: string;
  oldPostId?: string;
  newPostId?: string;
  oldPositionLevel?: string;
  newPositionLevel?: string;
  oldSalary?: string;
  newSalary?: string;
  reason?: string;
  applied: number;
  sourceRequestId?: string;
  flowInstanceId?: string;
  createTime?: string;
}

export interface ChangeForm {
  employeeId: string;
  changeType: number;
  effectiveDate: string;
  newDeptId?: string;
  newPostId?: string;
  newPositionLevel?: string;
  newSalary?: string;
  reason?: string;
  flowInstanceId?: string;
  sourceRequestId?: string;
}

export function listChange(query: any): AxiosPromise<ApiEnvelope<PageVo<ChangeVO>>> {
  return request({ url: '/api/v1/hr/changes', method: 'get', params: query });
}

export function getChange(changeId: string): AxiosPromise<ApiEnvelope<ChangeVO>> {
  return request({ url: `/api/v1/hr/changes/${changeId}`, method: 'get' });
}

export function addChange(data: ChangeForm) {
  return request({
    url: '/api/v1/hr/changes',
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

export function applyDueChanges() {
  return request({ url: '/api/v1/hr/changes/apply-due', method: 'post' });
}

// ------------------------------------------------------------- 合同（P1，需求 HR-09）

export interface ContractVO {
  id: string;
  employeeId: string;
  employeeName?: string;
  employeeNo?: string;
  contractNo?: string;
  contractType: string;
  startDate: string;
  endDate?: string;
  signDate?: string;
  renewCount: number;
  status: number;
  fileId?: string;
  daysToExpire?: number;
  remark?: string;
}

export interface ContractForm {
  id?: string;
  employeeId: string;
  contractNo?: string;
  contractType: string;
  startDate: string;
  endDate?: string;
  signDate?: string;
  renewCount?: number;
  status?: number;
  fileId?: string;
  remark?: string;
}

export function listContract(query: any): AxiosPromise<ApiEnvelope<PageVo<ContractVO>>> {
  return request({ url: '/api/v1/hr/contracts', method: 'get', params: query });
}

export function getContract(contractId: string): AxiosPromise<ApiEnvelope<ContractVO>> {
  return request({ url: `/api/v1/hr/contracts/${contractId}`, method: 'get' });
}

export function addContract(data: ContractForm) {
  return request({ url: '/api/v1/hr/contracts', method: 'post', data });
}

export function updateContract(contractId: string, data: ContractForm) {
  return request({ url: `/api/v1/hr/contracts/${contractId}`, method: 'put', data });
}

export function delContract(contractId: string) {
  return request({ url: `/api/v1/hr/contracts/${contractId}`, method: 'delete' });
}

export function expiringContracts(days = 30): AxiosPromise<ApiEnvelope<ContractVO[]>> {
  return request({ url: '/api/v1/hr/contracts/expiring', method: 'get', params: { days } });
}

// ------------------------------------------------------------- 员工自助（P1，需求 HR-10）

export interface SelfProfileVO {
  employee: EmployeeVO;
  educations: EducationVO[];
  works: WorkVO[];
  contracts: ContractVO[];
}

export function getSelfProfile(): AxiosPromise<ApiEnvelope<SelfProfileVO>> {
  return request({ url: '/api/v1/hr/profile/self', method: 'get' });
}

export function updateSelfProfile(data: { phone?: string; email?: string }) {
  return request({ url: '/api/v1/hr/profile/self', method: 'put', data });
}

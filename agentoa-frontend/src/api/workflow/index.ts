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

/** 分页响应（docs/05 1.4） */
export interface PageVo<T = any> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  pages: number;
}

export interface CategoryVO {
  id: string;
  code: string;
  name: string;
  sort: number;
  status: string;
  remark?: string;
}

export interface CategoryForm {
  code: string;
  name: string;
  sort?: number;
  status?: string;
  remark?: string;
}

export interface DefinitionVersionVO {
  versionNo: number;
  bpmnResource: string;
  compiled?: boolean;
  validationSummary?: string;
  status: string;
  publishedTime?: string;
}

export interface DefinitionVO {
  id: string;
  processKey: string;
  processName: string;
  categoryId: string;
  categoryName?: string;
  formKey: string;
  businessType: string;
  currentVersionNo: number;
  status: string;
  icon?: string;
  sort: number;
  remark?: string;
  form?: FormVO;
  chain?: string;
  builtin?: boolean;
  versions: DefinitionVersionVO[];
}

// ------------------------------------------------------------- 结构化审批链（唯一流程结构输入）

export type FlowSignMode = 'SINGLE' | 'COUNTERSIGN' | 'EITHERSIGN';

export type FlowNodeType = 'approve' | 'cc' | 'branch';

export type FlowConditionOp = 'gt' | 'ge' | 'lt' | 'le' | 'eq' | 'ne';

export interface FlowCondition {
  field: 'amount';
  op: FlowConditionOp;
  value: number;
}

export interface FlowBranch {
  id: string;
  name?: string;
  /** 为空表示默认分支 */
  condition?: FlowCondition | null;
  nodes: FlowChainNode[];
}

export interface FlowChainNode {
  id: string;
  type: FlowNodeType;
  name: string;
  /** UI 语义标记（如 handle=办理）：仅用于向导图标/文案区分，编译器忽略 */
  kind?: string;
  /** SELF / LEADER / DEPT_HEAD / USER_SELECT / ROLE:key / WHITELIST:id,id */
  assigneeRule?: string;
  signMode?: FlowSignMode;
  /** type=branch */
  branches?: FlowBranch[];
  /** type=branch 时分支节点的嵌套节点 */
  nodes?: FlowChainNode[];
}

export interface FlowChainConfig {
  chainVersion: number;
  nodes: FlowChainNode[];
}

export interface DefinitionPayload {
  processKey?: string;
  processName: string;
  categoryId: string;
  businessType?: string;
  icon?: string;
  sort?: number;
  remark?: string;
  form: { formKey: string; formName: string; schema: FormSchema };
  chain: FlowChainConfig;
}

export interface FormVO {
  formKey: string;
  formName: string;
  versionNo: number;
  status: string;
  schema: string;
}

export interface FormFieldOption {
  label: string;
  value: string | number;
}

export interface FormField {
  key: string;
  label: string;
  type: 'input' | 'textarea' | 'number' | 'select' | 'date' | 'datetime' | 'list' | 'file' | 'image';
  required?: boolean;
  readonly?: boolean;
  options?: FormFieldOption[];
  itemFields?: FormField[];
}

export interface FormSchema {
  schemaVersion: number;
  fields: FormField[];
}

export interface InstanceVO {
  id: string;
  processKey: string;
  processName: string;
  definitionVersionNo: number;
  businessType: string;
  businessId: string;
  businessKey?: string;
  submissionNo: number;
  title: string;
  initiatorUserId: string;
  initiatorName?: string;
  priority: number;
  status: number;
  currentTaskName?: string;
  currentAssignees?: string;
  startTime?: string;
  endTime?: string;
  duration?: number;
  lockVersion: number;
}

export interface TaskActionVO {
  taskName?: string;
  action: string;
  operatorUserId: string;
  operatorName?: string;
  oldAssigneeName?: string;
  newAssigneeName?: string;
  comment?: string;
  actionTime: string;
}

export interface TaskVO {
  taskId: string;
  instanceId: string;
  processInstanceId: string;
  taskName: string;
  taskDefKey?: string;
  assigneeId?: string;
  assigneeName?: string;
  businessType: string;
  businessId: string;
  title: string;
  instanceStatus: number;
  initiatorName?: string;
  createTime?: string;
  endTime?: string;
  comment?: string;
}

export interface InstanceDetailVO extends InstanceVO {
  formKey?: string;
  formName?: string;
  formVersionNo?: number;
  formSchema?: string;
  formData?: string;
  actions: TaskActionVO[];
  currentTasks: TaskVO[];
}

export interface InstanceStartVO {
  instanceId: string;
  processInstanceId: string;
  businessKey?: string;
  currentTasks: { taskId: string; taskName: string; assigneeId?: string; assigneeName?: string }[];
}

export interface BusinessRequestVO {
  id: string;
  businessType: string;
  userId: string;
  employeeId?: string;
  title: string;
  status: number;
  formData?: string;
  flowInstanceId?: string;
  submissionNo: number;
  lockVersion: number;
  createTime?: string;
  updateTime?: string;
}

function newIdempotencyKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `idem-${Date.now()}-${Math.random()}`;
}

// ------------------------------------------------------------- 分类

export function listCategories(): AxiosPromise<ApiEnvelope<CategoryVO[]>> {
  return request({ url: '/api/v1/wf/categories', method: 'get' });
}

export function addCategory(data: CategoryForm) {
  return request({ url: '/api/v1/wf/categories', method: 'post', data });
}

export function updateCategory(categoryId: string, data: CategoryForm) {
  return request({ url: `/api/v1/wf/categories/${categoryId}`, method: 'put', data });
}

export function delCategory(categoryId: string) {
  return request({ url: `/api/v1/wf/categories/${categoryId}`, method: 'delete' });
}

// ------------------------------------------------------------- 定义与表单

export function listDefinitions(): AxiosPromise<ApiEnvelope<DefinitionVO[]>> {
  return request({ url: '/api/v1/wf/definitions', method: 'get' });
}

export function getDefinition(definitionId: string): AxiosPromise<ApiEnvelope<DefinitionVO>> {
  return request({ url: `/api/v1/wf/definitions/${definitionId}`, method: 'get' });
}

/** 创建流程定义（基础信息 + 表单设计 + 结构化审批链） */
export function createDefinition(data: DefinitionPayload) {
  return request({
    url: '/api/v1/wf/definitions',
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

/** 更新流程定义；form/chain 变化会产出新版本 */
export function updateDefinition(definitionId: string, data: Partial<DefinitionPayload>) {
  return request({
    url: `/api/v1/wf/definitions/${definitionId}`,
    method: 'put',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

/** 启用/停用：PUBLISHED=启用、RETIRED=停用、DRAFT=草稿 */
export function changeDefinitionStatus(definitionId: string, status: 'PUBLISHED' | 'RETIRED' | 'DRAFT') {
  return request({
    url: `/api/v1/wf/definitions/${definitionId}/status`,
    method: 'put',
    data: { status },
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

/** 删除流程定义（内置模板与已有实例的定义受保护） */
export function removeDefinition(definitionId: string) {
  return request({
    url: `/api/v1/wf/definitions/${definitionId}`,
    method: 'delete',
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

/** 一键启用全部内置模板，返回本次启用数量 */
export function enableBuiltinTemplates() {
  return request({
    url: '/api/v1/wf/definitions/builtin/enable-all',
    method: 'post',
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

export function publishDefinition(definitionId: string, versionNo?: number) {
  return request({
    url: `/api/v1/wf/definitions/${definitionId}/publish`,
    method: 'post',
    params: versionNo ? { versionNo } : {}
  });
}

export function getDefinitionXml(definitionId: string, versionNo?: number): AxiosPromise<ApiEnvelope<string>> {
  return request({ url: `/api/v1/wf/definitions/${definitionId}/xml`, method: 'get', params: versionNo ? { versionNo } : {} });
}

export function getDefinitionDiagram(definitionId: string, versionNo?: number) {
  return request({
    url: `/api/v1/wf/definitions/${definitionId}/diagram`,
    method: 'get',
    responseType: 'blob',
    params: versionNo ? { versionNo } : {}
  });
}

export function listForms(): AxiosPromise<ApiEnvelope<FormVO[]>> {
  return request({ url: '/api/v1/wf/forms', method: 'get' });
}

export function getForm(formKey: string): AxiosPromise<ApiEnvelope<FormVO>> {
  return request({ url: `/api/v1/wf/forms/${formKey}`, method: 'get' });
}

// ------------------------------------------------------------- 流程实例

export function startInstance(data: { businessType: string; businessId: string; title?: string; priority?: number; lockVersion?: number }) {
  return request({
    url: '/api/v1/wf/instances/start',
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

export function listInstances(params: {
  scope?: string;
  businessType?: string;
  pageNum?: number;
  pageSize?: number;
}): AxiosPromise<ApiEnvelope<PageVo<InstanceVO>>> {
  return request({ url: '/api/v1/wf/instances', method: 'get', params });
}

export function getInstance(instanceId: string): AxiosPromise<ApiEnvelope<InstanceDetailVO>> {
  return request({ url: `/api/v1/wf/instances/${instanceId}`, method: 'get' });
}

export function getInstanceHistory(instanceId: string): AxiosPromise<ApiEnvelope<TaskActionVO[]>> {
  return request({ url: `/api/v1/wf/instances/${instanceId}/history`, method: 'get' });
}

export function getInstanceDiagram(instanceId: string) {
  return request({ url: `/api/v1/wf/instances/${instanceId}/diagram`, method: 'get', responseType: 'blob' });
}

export function revokeInstance(instanceId: string, lockVersion?: number) {
  return request({
    url: `/api/v1/wf/instances/${instanceId}/revoke`,
    method: 'put',
    data: { lockVersion },
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

// ------------------------------------------------------------- 任务

export function listTodo(params: { pageNum?: number; pageSize?: number }): AxiosPromise<ApiEnvelope<PageVo<TaskVO>>> {
  return request({ url: '/api/v1/wf/tasks/todo', method: 'get', params });
}

export function listDone(params: { pageNum?: number; pageSize?: number }): AxiosPromise<ApiEnvelope<PageVo<TaskVO>>> {
  return request({ url: '/api/v1/wf/tasks/done', method: 'get', params });
}

export function getTask(taskId: string): AxiosPromise<ApiEnvelope<TaskVO>> {
  return request({ url: `/api/v1/wf/tasks/${taskId}`, method: 'get' });
}

export function completeTask(taskId: string, data: { action: 'agree' | 'reject'; comment?: string; lockVersion?: number }) {
  return request({
    url: `/api/v1/wf/tasks/${taskId}/complete`,
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

export function transferTask(taskId: string, data: { targetUserId: string; reason?: string }) {
  return request({
    url: `/api/v1/wf/tasks/${taskId}/transfer`,
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

// ------------------------------------------------------------- P1 任务增强（退回/加签/催办/抄送）

export interface CcVO {
  id: string;
  instanceId: string;
  title?: string;
  businessType?: string;
  businessId?: string;
  senderUserId: string;
  senderName?: string;
  comment?: string;
  instanceStatus?: number;
  createTime?: string;
  readTime?: string;
}

export function returnTask(taskId: string, data: { toInitiator?: boolean; targetTaskKey?: string; comment?: string; lockVersion?: number }) {
  return request({
    url: `/api/v1/wf/tasks/${taskId}/return`,
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

export function addsignTask(taskId: string, data: { assigneeIds: string[]; position: 'before' | 'after'; reason?: string }) {
  return request({
    url: `/api/v1/wf/tasks/${taskId}/addsign`,
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

export function urgeTask(taskId: string, comment?: string) {
  return request({ url: `/api/v1/wf/tasks/${taskId}/urge`, method: 'post', data: { comment } });
}

export function listCc(params: { pageNum?: number; pageSize?: number }): AxiosPromise<ApiEnvelope<PageVo<CcVO>>> {
  return request({ url: '/api/v1/wf/tasks/cc', method: 'get', params });
}

export function ccInstance(instanceId: string, data: { userIds: string[]; comment?: string }) {
  return request({ url: `/api/v1/wf/instances/${instanceId}/cc`, method: 'post', data });
}

// ------------------------------------------------------------- P1 实例监控（挂起/恢复/终止/超时）

export function suspendInstance(instanceId: string) {
  return request({ url: `/api/v1/wf/instances/${instanceId}/suspend`, method: 'put' });
}

export function resumeInstance(instanceId: string) {
  return request({ url: `/api/v1/wf/instances/${instanceId}/resume`, method: 'put' });
}

export function terminateInstance(instanceId: string, reason?: string) {
  return request({ url: `/api/v1/wf/instances/${instanceId}/terminate`, method: 'put', data: { reason } });
}

export function timeoutScan(hours = 24) {
  return request({ url: '/api/v1/wf/instances/timeout-scan', method: 'post', params: { hours } });
}

// ------------------------------------------------------------- P1 委托代理（WF-12）

export interface DelegateVO {
  id: string;
  ownerId: string;
  ownerName?: string;
  delegateId: string;
  delegateName?: string;
  startDate: string;
  endDate: string;
  processKeys?: string;
  status: number;
  remark?: string;
}

export interface DelegateForm {
  delegateId: string;
  startDate: string;
  endDate: string;
  processKeys?: string;
  status?: number;
  remark?: string;
}

export function listDelegates(ownerId?: string): AxiosPromise<ApiEnvelope<DelegateVO[]>> {
  return request({ url: '/api/v1/wf/delegates', method: 'get', params: { ownerId } });
}

export function addDelegate(data: DelegateForm) {
  return request({ url: '/api/v1/wf/delegates', method: 'post', data });
}

export function updateDelegate(delegateId: string, data: DelegateForm) {
  return request({ url: `/api/v1/wf/delegates/${delegateId}`, method: 'put', data });
}

export function delDelegate(delegateId: string) {
  return request({ url: `/api/v1/wf/delegates/${delegateId}`, method: 'delete' });
}

// ------------------------------------------------------------- 业务承接单

export interface RequestGroup {
  create(data: any): any;
  update(id: string, data: any): any;
  get(id: string): AxiosPromise<ApiEnvelope<BusinessRequestVO>>;
  list(params: { pageNum?: number; pageSize?: number }): AxiosPromise<ApiEnvelope<PageVo<BusinessRequestVO>>>;
  submit(id: string, lockVersion?: number): AxiosPromise<ApiEnvelope<InstanceStartVO>>;
}

function requestGroup(base: string): RequestGroup {
  return {
    create: (data: any) => request({ url: base, method: 'post', data, headers: { 'Idempotency-Key': newIdempotencyKey() } }),
    update: (id: string, data: any) => request({ url: `${base}/${id}`, method: 'put', data }),
    get: (id: string) => request({ url: `${base}/${id}`, method: 'get' }),
    list: (params: any) => request({ url: base, method: 'get', params }),
    submit: (id: string, lockVersion?: number) =>
      request({
        url: `${base}/${id}/submit`,
        method: 'post',
        data: { lockVersion },
        headers: { 'Idempotency-Key': newIdempotencyKey() }
      })
  };
}

export const leaveApi = requestGroup('/api/v1/attendance/leaves');
export const overtimeApi = requestGroup('/api/v1/attendance/overtimes');
export const correctionApi = requestGroup('/api/v1/attendance/corrections');
export const reimburseApi = requestGroup('/api/v1/finance/reimburses');
export const lifecycleApi = requestGroup('/api/v1/hr/lifecycle-requests');

/** 通用 OA 申请（M5）：纯 OA 表单/自定义流程不绑定业务表，直接走结构化审批链 */
export const genericApi = {
  ...requestGroup('/api/v1/wf/generic-requests'),
  /** 建单 + 发起流程一并完成 */
  launch: (data: { definitionId: string; title?: string; formData?: string; remark?: string }) =>
    request({
      url: '/api/v1/wf/generic-requests/launch',
      method: 'post',
      data,
      headers: { 'Idempotency-Key': newIdempotencyKey() }
    })
};
export function requestApiFor(businessType: string): RequestGroup {
  switch (businessType) {
    case 'leave':
      return leaveApi;
    case 'overtime':
      return overtimeApi;
    case 'correction':
      return correctionApi;
    case 'reimburse':
      return reimburseApi;
    case 'regularize':
    case 'offboard':
      return lifecycleApi;
    default:
      // 26 个内置模板与自定义流程走通用承接
      return genericApi;
  }
}

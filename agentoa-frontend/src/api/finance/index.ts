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

function newIdempotencyKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `idem-${Date.now()}-${Math.random()}`;
}

// ------------------------------------------------------------- VO / Form

export interface ExpenseTypeVO {
  id: string;
  parentId: string;
  name: string;
  code: string;
  sort: number;
  budgetControl: number;
  status: string;
  remark?: string;
  children?: ExpenseTypeVO[];
}

export interface ExpenseTypeForm {
  id?: string;
  parentId?: string;
  name: string;
  code: string;
  sort?: number;
  budgetControl?: number;
  status?: string;
  remark?: string;
}

export interface InvoiceVO {
  id: string;
  invoiceType: string;
  invoiceCode: string;
  invoiceNo: string;
  invoiceDate?: string;
  amount: string;
  fingerprint: string;
  fileId?: string;
  fileName?: string;
  ownerUserId: string;
  ownerName?: string;
  occupiedReimburseId?: string;
  paidReimburseId?: string;
  occupationStatus: 'FREE' | 'OCCUPIED' | 'PAID';
  createTime?: string;
}

export interface InvoiceForm {
  invoiceType: string;
  invoiceCode?: string;
  invoiceNo: string;
  invoiceDate?: string;
  amount: string;
  fileId?: string;
}

export interface ReimburseDetailForm {
  expenseType?: string;
  expenseTypeId?: string;
  occurDate?: string;
  amount: string;
  invoiceNo?: string;
  invoiceId?: string;
  description?: string;
}

export interface ReimburseForm {
  reimburseType?: string;
  totalAmount?: string;
  currency?: string;
  payMethod?: string;
  details: ReimburseDetailForm[];
  reason?: string;
  lockVersion?: number;
  remark?: string;
}

export interface BusinessRequestVO {
  id: string;
  businessType: string;
  userId: string;
  employeeId?: string;
  title: string;
  status: number;
  formData: string;
  flowInstanceId?: string;
  submissionNo: number;
  lockVersion: number;
  createTime?: string;
  updateTime?: string;
}

export interface PendingClaimVO {
  id: string;
  reimburseNo: string;
  userId: string;
  applicantName?: string;
  deptId?: string;
  deptName?: string;
  totalAmount: string;
  status: number;
  createTime?: string;
  updateTime?: string;
}

export interface PaymentVO {
  id: string;
  paymentNo: string;
  reimburseId: string;
  reimburseNo?: string;
  applicantName?: string;
  amount: string;
  payDate?: string;
  paymentMethod: string;
  voucherNo?: string;
  payStatus: number;
  operatorId: string;
  operatorName?: string;
  createTime?: string;
  remark?: string;
}

export interface PaymentForm {
  lockVersion?: number;
  amount: string;
  payDate: string;
  paymentMethod: string;
  voucherNo?: string;
  remark?: string;
}

export interface ExpenseReportVO {
  deptId: string;
  deptName?: string;
  expenseTypeName?: string;
  itemCount: number;
  totalAmount: string;
}

// ------------------------------------------------------------- 费用类型

export function listExpenseType(): AxiosPromise<ApiEnvelope<ExpenseTypeVO[]>> {
  return request({ url: '/api/v1/finance/expense-types', method: 'get' });
}
export function addExpenseType(data: ExpenseTypeForm) {
  return request({ url: '/api/v1/finance/expense-types', method: 'post', data });
}
export function updateExpenseType(id: string, data: ExpenseTypeForm) {
  return request({ url: `/api/v1/finance/expense-types/${id}`, method: 'put', data });
}
export function delExpenseType(id: string) {
  return request({ url: `/api/v1/finance/expense-types/${id}`, method: 'delete' });
}

// ------------------------------------------------------------- 发票

export function listInvoice(query: any): AxiosPromise<ApiEnvelope<PageVo<InvoiceVO>>> {
  return request({ url: '/api/v1/finance/invoices', method: 'get', params: query });
}
export function getInvoice(id: string): AxiosPromise<ApiEnvelope<InvoiceVO>> {
  return request({ url: `/api/v1/finance/invoices/${id}`, method: 'get' });
}
export function addInvoice(data: InvoiceForm) {
  return request({ url: '/api/v1/finance/invoices', method: 'post', data });
}
export function delInvoice(id: string) {
  return request({ url: `/api/v1/finance/invoices/${id}`, method: 'delete' });
}
export function downloadInvoice(id: string) {
  return request({ url: `/api/v1/finance/invoices/${id}/download`, method: 'get', responseType: 'blob' });
}

// ------------------------------------------------------------- 报销单

export function listReimburses(query: any): AxiosPromise<ApiEnvelope<PageVo<BusinessRequestVO>>> {
  return request({ url: '/api/v1/finance/reimburses', method: 'get', params: query });
}
export function getReimburse(id: string): AxiosPromise<ApiEnvelope<BusinessRequestVO>> {
  return request({ url: `/api/v1/finance/reimburses/${id}`, method: 'get' });
}
export function createReimburse(data: ReimburseForm) {
  return request({
    url: '/api/v1/finance/reimburses',
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}
export function updateReimburse(id: string, data: ReimburseForm) {
  return request({ url: `/api/v1/finance/reimburses/${id}`, method: 'put', data });
}
export function deleteReimburse(id: string) {
  return request({ url: `/api/v1/finance/reimburses/${id}`, method: 'delete' });
}
export function submitReimburse(id: string, lockVersion?: number) {
  return request({
    url: `/api/v1/finance/reimburses/${id}/submit`,
    method: 'post',
    data: { lockVersion },
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

// ------------------------------------------------------------- 付款

export function payReimburse(id: string, data: PaymentForm) {
  return request({
    url: `/api/v1/finance/reimburses/${id}/pay`,
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}
export function listPendingClaims(query: any): AxiosPromise<ApiEnvelope<PageVo<PendingClaimVO>>> {
  return request({ url: '/api/v1/finance/reimburses/pending', method: 'get', params: query });
}
export function listPayment(query: any): AxiosPromise<ApiEnvelope<PageVo<PaymentVO>>> {
  return request({ url: '/api/v1/finance/payments', method: 'get', params: query });
}
export function getPayment(id: string): AxiosPromise<ApiEnvelope<PaymentVO>> {
  return request({ url: `/api/v1/finance/payments/${id}`, method: 'get' });
}

// ------------------------------------------------------------- 预算（P1，FN-03）

export interface BudgetVO {
  id: string;
  budgetCode: string;
  budgetName?: string;
  budgetType: number;
  ownerId: string;
  year: number;
  quarter?: number;
  totalAmount: string;
  usedAmount: string;
  frozenAmount: string;
  availableAmount: string;
  warnThreshold: number;
  warn: boolean;
  status: number;
  remark?: string;
}

export interface BudgetForm {
  budgetCode?: string;
  budgetName?: string;
  budgetType: number;
  ownerId: string;
  year: number;
  quarter?: number;
  totalAmount: string;
  warnThreshold?: number;
  status?: number;
  remark?: string;
}

export function listBudget(query: any): AxiosPromise<ApiEnvelope<BudgetVO[]>> {
  return request({ url: '/api/v1/finance/budgets', method: 'get', params: query });
}

export function getBudget(id: string): AxiosPromise<ApiEnvelope<BudgetVO>> {
  return request({ url: `/api/v1/finance/budgets/${id}`, method: 'get' });
}

export function addBudget(data: BudgetForm) {
  return request({ url: '/api/v1/finance/budgets', method: 'post', data });
}

export function updateBudget(id: string, data: BudgetForm) {
  return request({ url: `/api/v1/finance/budgets/${id}`, method: 'put', data });
}

export function closeBudget(id: string) {
  return request({ url: `/api/v1/finance/budgets/${id}`, method: 'delete' });
}

// ------------------------------------------------------------- 报表

export function listExpenseReport(query: any): AxiosPromise<ApiEnvelope<ExpenseReportVO[]>> {
  return request({ url: '/api/v1/finance/reports/expense', method: 'get', params: query });
}
export function exportExpenseReport(query: any) {
  return request({ url: '/api/v1/finance/reports/export', method: 'get', params: query, responseType: 'blob' });
}

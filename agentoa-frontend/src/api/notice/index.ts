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

export interface AnnouncementVO {
  id: string;
  title: string;
  content: string;
  noticeType?: string;
  publisherId: string;
  publisherName?: string;
  publishTime?: string;
  effectiveStart?: string;
  effectiveEnd?: string;
  scopeType: number;
  scopeValues?: string;
  isTop: number;
  isPopup: number;
  status: number;
  readCount: number;
  audienceCount: number;
  attachments?: string;
  read?: boolean;
  createTime?: string;
  updateTime?: string;
}

export interface AnnouncementForm {
  title: string;
  content: string;
  noticeType?: string;
  scopeType: string;
  scopeValues?: string;
  isTop?: number;
  isPopup?: number;
  attachments?: string;
  remark?: string;
}

export interface ReadStatusVO {
  noticeId: string;
  audienceCount: number;
  readCount: number;
  readUsers: { userId: string; nickname?: string; readTime?: string }[];
  unreadUsers: { userId: string; nickname?: string }[];
}

export interface MessageVO {
  id: string;
  type: string;
  title: string;
  content?: string;
  bizType?: string;
  bizId?: string;
  isRead: number;
  createTime?: string;
}

export interface UnreadCountVO {
  total: number;
  todo: number;
  notice: number;
  mention: number;
  system: number;
}

export interface PreferenceVO {
  msgType: string;
  enabled: string;
}

export interface OutboxEventVO {
  id: string;
  eventId: string;
  receiverId: string;
  eventType: string;
  title?: string;
  status: string;
  retryCount: number;
  redeliverCount: number;
  redeliveredBy?: string;
  redeliveredTime?: string;
  nextAttemptTime?: string;
  lastError?: string;
  createTime?: string;
  processedTime?: string;
}

// ------------------------------------------------------------- 公告

export function listAnnouncement(query: any): AxiosPromise<ApiEnvelope<PageVo<AnnouncementVO>>> {
  return request({ url: '/api/v1/notice/list', method: 'get', params: query });
}
export function getAnnouncement(id: string): AxiosPromise<ApiEnvelope<AnnouncementVO>> {
  return request({ url: `/api/v1/notice/${id}`, method: 'get' });
}
export function addAnnouncement(data: AnnouncementForm) {
  return request({ url: '/api/v1/notice', method: 'post', data });
}
export function updateAnnouncement(id: string, data: AnnouncementForm) {
  return request({ url: `/api/v1/notice/${id}`, method: 'put', data });
}
export function delAnnouncement(id: string) {
  return request({ url: `/api/v1/notice/${id}`, method: 'delete' });
}
export function publishAnnouncement(id: string) {
  return request({
    url: `/api/v1/notice/${id}/publish`,
    method: 'post',
    data: {},
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}
export function revokeAnnouncement(id: string) {
  return request({ url: `/api/v1/notice/${id}/revoke`, method: 'put' });
}
export function getReadStatus(id: string): AxiosPromise<ApiEnvelope<ReadStatusVO>> {
  return request({ url: `/api/v1/notice/${id}/read-status`, method: 'get' });
}
export function markNoticeRead(id: string) {
  return request({ url: `/api/v1/notice/${id}/read`, method: 'put' });
}

// ------------------------------------------------------------- 消息中心

export function listMessage(query: any): AxiosPromise<ApiEnvelope<PageVo<MessageVO>>> {
  return request({ url: '/api/v1/messages', method: 'get', params: query });
}
export function unreadCount(): AxiosPromise<ApiEnvelope<UnreadCountVO>> {
  return request({ url: '/api/v1/messages/unread-count', method: 'get' });
}
export function markMessageRead(id: string) {
  return request({ url: `/api/v1/messages/${id}/read`, method: 'put' });
}
export function markAllRead() {
  return request({ url: '/api/v1/messages/read-all', method: 'put' });
}
export function delMessage(id: string) {
  return request({ url: `/api/v1/messages/${id}`, method: 'delete' });
}

// ------------------------------------------------------------- 偏好与消息事件

export function getPreferences(): AxiosPromise<ApiEnvelope<PreferenceVO[]>> {
  return request({ url: '/api/v1/notice/preferences', method: 'get' });
}
export function updatePreferences(items: PreferenceVO[]): AxiosPromise<ApiEnvelope<PreferenceVO[]>> {
  return request({ url: '/api/v1/notice/preferences', method: 'put', data: items });
}
export function listOutboxEvents(query: any): AxiosPromise<ApiEnvelope<PageVo<OutboxEventVO>>> {
  return request({ url: '/api/v1/messages/outbox', method: 'get', params: query });
}
export function redeliverOutboxEvent(id: string) {
  return request({ url: `/api/v1/messages/outbox/${id}/redeliver`, method: 'post', data: {} });
}

// ------------------------------------------------------------- 通知模板（NC-04）

export interface NoticeTemplateVO {
  id: string;
  templateCode: string;
  name: string;
  titleTpl: string;
  contentTpl: string;
  msgType: string;
  vars?: string[];
  status: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface NoticeTemplateForm {
  templateCode: string;
  name: string;
  titleTpl: string;
  contentTpl: string;
  msgType: string;
  vars?: string[];
  status: string;
  remark?: string;
}

export interface TemplateSendForm {
  scopeType: string;
  scopeValues?: string;
  vars?: Record<string, string>;
}

export function listTemplate(query: any): AxiosPromise<ApiEnvelope<PageVo<NoticeTemplateVO>>> {
  return request({ url: '/api/v1/notice/templates/list', method: 'get', params: query });
}
export function getTemplate(id: string): AxiosPromise<ApiEnvelope<NoticeTemplateVO>> {
  return request({ url: `/api/v1/notice/templates/${id}`, method: 'get' });
}
export function addTemplate(data: NoticeTemplateForm) {
  return request({ url: '/api/v1/notice/templates', method: 'post', data });
}
export function updateTemplate(id: string, data: NoticeTemplateForm) {
  return request({ url: `/api/v1/notice/templates/${id}`, method: 'put', data });
}
export function delTemplate(id: string) {
  return request({ url: `/api/v1/notice/templates/${id}`, method: 'delete' });
}
export function sendTemplate(code: string, data: TemplateSendForm) {
  return request({ url: `/api/v1/notice/templates/${code}/send`, method: 'post', data });
}

// ------------------------------------------------------------- 定时推送（NC-04）

export interface ScheduledPushVO {
  id: string;
  name: string;
  pushType: number;
  announcementId?: string;
  announcementTitle?: string;
  templateId?: string;
  templateCode?: string;
  templateName?: string;
  scopeType?: number;
  scopeValues?: string;
  vars?: Record<string, string>;
  scheduleType: number;
  runAt?: string;
  cronExpr?: string;
  nextRunTime?: string;
  lastRunTime?: string;
  runCount: number;
  lastError?: string;
  status: number;
  remark?: string;
  createTime?: string;
}

export interface ScheduledPushForm {
  name: string;
  pushType: string;
  announcementId?: string;
  templateId?: string;
  scopeType?: string;
  scopeValues?: string;
  vars?: Record<string, string>;
  scheduleType: string;
  runAt?: string;
  cronExpr?: string;
  status?: string;
  remark?: string;
}

export interface PushRunVO {
  id: string;
  pushId: string;
  eventKey: string;
  slotTime: string;
  status: number;
  receiverCount: number;
  error?: string;
  createTime: string;
}

export function listPush(query: any): AxiosPromise<ApiEnvelope<PageVo<ScheduledPushVO>>> {
  return request({ url: '/api/v1/notice/push/list', method: 'get', params: query });
}
export function getPush(id: string): AxiosPromise<ApiEnvelope<ScheduledPushVO>> {
  return request({ url: `/api/v1/notice/push/${id}`, method: 'get' });
}
export function addPush(data: ScheduledPushForm) {
  return request({ url: '/api/v1/notice/push', method: 'post', data });
}
export function updatePush(id: string, data: ScheduledPushForm) {
  return request({ url: `/api/v1/notice/push/${id}`, method: 'put', data });
}
export function delPush(id: string) {
  return request({ url: `/api/v1/notice/push/${id}`, method: 'delete' });
}
export function pausePush(id: string) {
  return request({ url: `/api/v1/notice/push/${id}/pause`, method: 'put' });
}
export function resumePush(id: string) {
  return request({ url: `/api/v1/notice/push/${id}/resume`, method: 'put' });
}
export function listPushRuns(id: string, query: any): AxiosPromise<ApiEnvelope<PageVo<PushRunVO>>> {
  return request({ url: `/api/v1/notice/push/${id}/runs`, method: 'get', params: query });
}
export function runPushNow() {
  return request({ url: '/api/v1/notice/push/run', method: 'post', data: {} });
}

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

// ------------------------------------------------------------- 日程
export interface EventAttendee {
  userId: string;
  nickname: string;
  responseStatus: string;
}

export interface EventVO {
  id: string;
  lockVersion: number;
  title: string;
  description: string;
  eventType: number;
  startTime: string;
  endTime: string;
  isAllDay: number;
  location: string;
  organizerId: string;
  organizerName: string;
  roomId: string;
  roomName: string;
  visibility: number;
  remindMinutes: number;
  status: number;
  attendees: EventAttendee[];
  myResponse: string;
  canManage: boolean;
  createTime: string;
  updateTime: string;
}

export interface EventForm {
  lockVersion?: number;
  title: string;
  description?: string;
  eventType?: string;
  startTime: string;
  endTime: string;
  isAllDay?: number;
  location?: string;
  roomId?: string;
  visibility?: string;
  remindMinutes?: number;
  attendeeIds?: string[];
}

export function listEvent(query: any): AxiosPromise<ApiEnvelope<PageVo<EventVO>>> {
  return request({ url: '/api/v1/calendar/events', method: 'get', params: query });
}

export function getEvent(id: string): AxiosPromise<ApiEnvelope<EventVO>> {
  return request({ url: `/api/v1/calendar/events/${id}`, method: 'get' });
}

export function addEvent(data: EventForm) {
  return request({ url: '/api/v1/calendar/events', method: 'post', data });
}

export function updateEvent(id: string, data: EventForm) {
  return request({ url: `/api/v1/calendar/events/${id}`, method: 'put', data });
}

export function delEvent(id: string) {
  return request({ url: `/api/v1/calendar/events/${id}`, method: 'delete' });
}

export function acceptEvent(id: string) {
  return request({ url: `/api/v1/calendar/events/${id}/accept`, method: 'put', data: {} });
}

export function rejectEvent(id: string) {
  return request({ url: `/api/v1/calendar/events/${id}/reject`, method: 'put', data: {} });
}

// ------------------------------------------------------------- 会议室
export interface RoomVO {
  id: string;
  name: string;
  location: string;
  capacity: number;
  equipment: string;
  status: number;
  createTime: string;
  updateTime: string;
}

export interface RoomForm {
  name: string;
  location?: string;
  capacity?: number;
  equipment?: string;
  status?: string;
}

export function listRoom(query: any): AxiosPromise<ApiEnvelope<PageVo<RoomVO>>> {
  return request({ url: '/api/v1/calendar/rooms', method: 'get', params: query });
}

export function allRoom(): AxiosPromise<ApiEnvelope<RoomVO[]>> {
  return request({ url: '/api/v1/calendar/rooms/all', method: 'get' });
}

export function getRoom(id: string): AxiosPromise<ApiEnvelope<RoomVO>> {
  return request({ url: `/api/v1/calendar/rooms/${id}`, method: 'get' });
}

export function addRoom(data: RoomForm) {
  return request({ url: '/api/v1/calendar/rooms', method: 'post', data });
}

export function updateRoom(id: string, data: RoomForm) {
  return request({ url: `/api/v1/calendar/rooms/${id}`, method: 'put', data });
}

export function delRoom(id: string) {
  return request({ url: `/api/v1/calendar/rooms/${id}`, method: 'delete' });
}

// ------------------------------------------------------------- 会议室预约
export interface BookingVO {
  id: string;
  lockVersion: number;
  roomId: string;
  roomName: string;
  eventId: string;
  title: string;
  bookerId: string;
  bookerName: string;
  startTime: string;
  endTime: string;
  checkinTime: string;
  status: number;
  canManage: boolean;
  createTime: string;
}

export interface BookingForm {
  eventId?: string;
  title: string;
  startTime: string;
  endTime: string;
}

export function listBooking(roomId: string, query: any): AxiosPromise<ApiEnvelope<PageVo<BookingVO>>> {
  return request({ url: `/api/v1/calendar/rooms/${roomId}/bookings`, method: 'get', params: query });
}

export function bookRoom(roomId: string, data: BookingForm) {
  return request({
    url: `/api/v1/calendar/rooms/${roomId}/bookings`,
    method: 'post',
    data,
    headers: { 'Idempotency-Key': newIdempotencyKey() }
  });
}

export function checkinBooking(id: string) {
  return request({ url: `/api/v1/calendar/bookings/${id}/checkin`, method: 'put', data: {} });
}

export function cancelBooking(id: string) {
  return request({ url: `/api/v1/calendar/bookings/${id}/cancel`, method: 'put', data: {} });
}

// ------------------------------------------------------------- 任务
export interface TaskMember {
  userId: string;
  nickname: string;
}

export interface TaskVO {
  id: string;
  lockVersion: number;
  title: string;
  description: string;
  parentId: string;
  assignerId: string;
  assignerName: string;
  assigneeId: string;
  assigneeName: string;
  priority: number;
  status: number;
  startDate: string;
  dueDate: string;
  completedTime: string;
  progress: number;
  tags: string;
  members: TaskMember[];
  canManage: boolean;
  createTime: string;
  updateTime: string;
}

export interface TaskForm {
  lockVersion?: number;
  title: string;
  description?: string;
  parentId?: string;
  assigneeId?: string;
  priority?: string;
  startDate?: string;
  dueDate?: string;
  tags?: string;
  memberIds?: string[];
}

export interface TaskActivityVO {
  id: string;
  taskId: string;
  activityType: number;
  content: string;
  operatorId: string;
  operatorName: string;
  mentionIds: string;
  createTime: string;
}

export interface BoardColumn {
  status: number;
  statusName: string;
  tasks: TaskVO[];
}

export function listTask(query: any): AxiosPromise<ApiEnvelope<PageVo<TaskVO>>> {
  return request({ url: '/api/v1/tasks', method: 'get', params: query });
}

export function boardTask(): AxiosPromise<ApiEnvelope<{ columns: BoardColumn[] }>> {
  return request({ url: '/api/v1/tasks/board', method: 'get' });
}

export function getTask(id: string): AxiosPromise<ApiEnvelope<TaskVO>> {
  return request({ url: `/api/v1/tasks/${id}`, method: 'get' });
}

export function addTask(data: TaskForm) {
  return request({ url: '/api/v1/tasks', method: 'post', data });
}

export function updateTask(id: string, data: TaskForm) {
  return request({ url: `/api/v1/tasks/${id}`, method: 'put', data });
}

export function delTask(id: string) {
  return request({ url: `/api/v1/tasks/${id}`, method: 'delete' });
}

export function changeTaskStatus(id: string, data: { lockVersion?: number; status: string; comment?: string }) {
  return request({ url: `/api/v1/tasks/${id}/status`, method: 'put', data });
}

export function changeTaskProgress(id: string, data: { lockVersion?: number; progress: number; comment?: string }) {
  return request({ url: `/api/v1/tasks/${id}/progress`, method: 'put', data });
}

export function addTaskComment(id: string, data: { content: string; mentionIds?: string[] }) {
  return request({ url: `/api/v1/tasks/${id}/comments`, method: 'post', data });
}

export function listTaskComments(id: string): AxiosPromise<ApiEnvelope<TaskActivityVO[]>> {
  return request({ url: `/api/v1/tasks/${id}/comments`, method: 'get' });
}

export function listTaskActivities(id: string): AxiosPromise<ApiEnvelope<TaskActivityVO[]>> {
  return request({ url: `/api/v1/tasks/${id}/activities`, method: 'get' });
}

export function listTaskMembers(id: string): AxiosPromise<ApiEnvelope<TaskMember[]>> {
  return request({ url: `/api/v1/tasks/${id}/members`, method: 'get' });
}

export function addTaskMember(id: string, userId: string) {
  return request({ url: `/api/v1/tasks/${id}/members`, method: 'post', params: { userId } });
}

export function delTaskMember(id: string, userId: string) {
  return request({ url: `/api/v1/tasks/${id}/members/${userId}`, method: 'delete' });
}

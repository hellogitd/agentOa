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

// ------------------------------------------------------------- VO / Form

export interface SpaceVO {
  id: string;
  name: string;
  icon?: string;
  description?: string;
  spaceType: number;
  myRole?: string;
  memberCount?: number;
  createBy?: string;
  createByName?: string;
  createTime?: string;
  updateTime?: string;
  remark?: string;
}

export interface SpaceForm {
  name: string;
  icon?: string;
  description?: string;
  spaceType: string;
  remark?: string;
}

export interface MemberVO {
  id: string;
  spaceId: string;
  userId: string;
  nickname?: string;
  role: string;
  grantedBy?: string;
  grantedByName?: string;
  grantedTime?: string;
}

export interface MemberForm {
  userId: string;
  role: string;
}

export interface DocumentVO {
  id: string;
  spaceId: string;
  spaceName?: string;
  parentId: string;
  title: string;
  content?: string;
  tags?: string;
  docType?: string;
  version: number;
  status: number;
  isTop?: number;
  viewCount?: number;
  deletedAt?: string;
  myRole?: string;
  lastEditByName?: string;
  lastEditTime?: string;
  createBy?: string;
  createByName?: string;
  createTime?: string;
  updateTime?: string;
  hasDraft?: boolean;
}

export interface DocumentForm {
  spaceId: string;
  parentId?: string;
  title: string;
  content?: string;
  tags?: string;
  docType?: string;
  baseVersion?: number;
  changeSummary?: string;
}

export interface DocumentVersionVO {
  id: string;
  documentId: string;
  version: number;
  title?: string;
  content?: string;
  changeSummary?: string;
  createBy?: string;
  createByName?: string;
  createTime?: string;
}

export interface DocumentDraftVO {
  documentId: string;
  userId: string;
  baseVersion: number;
  title?: string;
  content?: string;
  updateTime?: string;
}

export interface SearchHitVO {
  documentId: string;
  title: string;
  highlight: string;
  spaceName?: string;
  lastEditTime?: string;
}

export interface KbFileVO {
  id: string;
  spaceId: string;
  spaceName?: string;
  documentId?: string;
  documentTitle?: string;
  fileId: string;
  fileName: string;
  fileExt?: string;
  fileSize?: number;
  contentType?: string;
  downloadCount?: number;
  deletedAt?: string;
  myRole?: string;
  createBy?: string;
  createByName?: string;
  createTime?: string;
}

// ------------------------------------------------------------- 空间与成员

export function listSpace(query: any): AxiosPromise<ApiEnvelope<PageVo<SpaceVO>>> {
  return request({ url: '/api/v1/knowledge/spaces', method: 'get', params: query });
}
export function getSpace(id: string): AxiosPromise<ApiEnvelope<SpaceVO>> {
  return request({ url: `/api/v1/knowledge/spaces/${id}`, method: 'get' });
}
export function addSpace(data: SpaceForm) {
  return request({ url: '/api/v1/knowledge/spaces', method: 'post', data });
}
export function updateSpace(id: string, data: SpaceForm) {
  return request({ url: `/api/v1/knowledge/spaces/${id}`, method: 'put', data });
}
export function delSpace(id: string) {
  return request({ url: `/api/v1/knowledge/spaces/${id}`, method: 'delete' });
}
export function listMember(spaceId: string): AxiosPromise<ApiEnvelope<MemberVO[]>> {
  return request({ url: `/api/v1/knowledge/spaces/${spaceId}/members`, method: 'get' });
}
export function addMember(spaceId: string, data: MemberForm) {
  return request({ url: `/api/v1/knowledge/spaces/${spaceId}/members`, method: 'post', data });
}
export function updateMember(spaceId: string, userId: string, data: MemberForm) {
  return request({ url: `/api/v1/knowledge/spaces/${spaceId}/members/${userId}`, method: 'put', data });
}
export function delMember(spaceId: string, userId: string) {
  return request({ url: `/api/v1/knowledge/spaces/${spaceId}/members/${userId}`, method: 'delete' });
}

// ------------------------------------------------------------- 文档

export function listDocument(query: any): AxiosPromise<ApiEnvelope<PageVo<DocumentVO>>> {
  return request({ url: '/api/v1/knowledge/documents', method: 'get', params: query });
}
export function getDocument(id: string): AxiosPromise<ApiEnvelope<DocumentVO>> {
  return request({ url: `/api/v1/knowledge/documents/${id}`, method: 'get' });
}
/** 批量详情（收藏列表等场景）：服务端按授权过滤，无权限/已删除文档不出现在结果中 */
export function getDocumentsBatch(ids: string[]): AxiosPromise<ApiEnvelope<DocumentVO[]>> {
  return request({ url: '/api/v1/knowledge/documents/batch', method: 'get', params: { ids: ids.join(',') } });
}
export function addDocument(data: DocumentForm) {
  return request({ url: '/api/v1/knowledge/documents', method: 'post', data });
}
export function updateDocument(id: string, data: DocumentForm) {
  return request({ url: `/api/v1/knowledge/documents/${id}`, method: 'put', data });
}
export function delDocument(id: string) {
  return request({ url: `/api/v1/knowledge/documents/${id}`, method: 'delete' });
}
export function restoreDocument(id: string) {
  return request({ url: `/api/v1/knowledge/documents/${id}/restore`, method: 'post', data: {} });
}
export function publishDocument(id: string) {
  return request({ url: `/api/v1/knowledge/documents/${id}/publish`, method: 'put' });
}
export function archiveDocument(id: string) {
  return request({ url: `/api/v1/knowledge/documents/${id}/archive`, method: 'put' });
}
export function listVersion(id: string): AxiosPromise<ApiEnvelope<DocumentVersionVO[]>> {
  return request({ url: `/api/v1/knowledge/documents/${id}/versions`, method: 'get' });
}
export function getVersion(id: string, version: number): AxiosPromise<ApiEnvelope<DocumentVersionVO>> {
  return request({ url: `/api/v1/knowledge/documents/${id}/versions/${version}`, method: 'get' });
}
export function rollbackVersion(id: string, version: number) {
  return request({ url: `/api/v1/knowledge/documents/${id}/rollback/${version}`, method: 'put' });
}
export function saveDraft(id: string, data: { title?: string; content?: string; baseVersion?: number }) {
  return request({ url: `/api/v1/knowledge/documents/${id}/draft`, method: 'put', data });
}
export function getDraft(id: string): AxiosPromise<ApiEnvelope<DocumentDraftVO>> {
  return request({ url: `/api/v1/knowledge/documents/${id}/draft`, method: 'get' });
}
export function searchDocument(query: any): AxiosPromise<ApiEnvelope<PageVo<SearchHitVO>>> {
  return request({ url: '/api/v1/knowledge/documents/search', method: 'get', params: query });
}

// ------------------------------------------------------------- 文件柜

export function listKbFile(query: any): AxiosPromise<ApiEnvelope<PageVo<KbFileVO>>> {
  return request({ url: '/api/v1/knowledge/files', method: 'get', params: query });
}
export function uploadKbFile(spaceId: string, file: File, documentId?: string) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('spaceId', spaceId);
  if (documentId) {
    formData.append('documentId', documentId);
  }
  return request({
    url: '/api/v1/knowledge/files',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  });
}
export function delKbFile(id: string) {
  return request({ url: `/api/v1/knowledge/files/${id}`, method: 'delete' });
}
export function restoreKbFile(id: string) {
  return request({ url: `/api/v1/knowledge/files/${id}/restore`, method: 'post', data: {} });
}
export function downloadKbFile(id: string) {
  return request({ url: `/api/v1/knowledge/files/${id}/download`, method: 'get', responseType: 'blob' });
}
export function previewKbFile(id: string) {
  return request({ url: `/api/v1/knowledge/files/${id}/preview`, method: 'get', responseType: 'blob' });
}

// ------------------------------------------------------------- 社交（P1，KB-06/KB-07）

export interface KbCommentVO {
  id: string;
  documentId: string;
  parentId: string;
  content: string;
  createBy: string;
  createByName?: string;
  createTime: string;
}

export interface KbSocialStatVO {
  documentId: string;
  likeCount: number;
  likedByMe: boolean;
  favoritedByMe: boolean;
}

export function listKbComment(documentId: string): AxiosPromise<ApiEnvelope<KbCommentVO[]>> {
  return request({ url: `/api/v1/knowledge/documents/${documentId}/comments`, method: 'get' });
}

export function addKbComment(documentId: string, data: { content: string; parentId?: string }) {
  return request({ url: `/api/v1/knowledge/documents/${documentId}/comments`, method: 'post', data });
}

export function delKbComment(documentId: string, commentId: string) {
  return request({ url: `/api/v1/knowledge/documents/${documentId}/comments/${commentId}`, method: 'delete' });
}

export function likeKbDocument(documentId: string) {
  return request({ url: `/api/v1/knowledge/documents/${documentId}/like`, method: 'post' });
}

export function unlikeKbDocument(documentId: string) {
  return request({ url: `/api/v1/knowledge/documents/${documentId}/like`, method: 'delete' });
}

export function favoriteKbDocument(documentId: string) {
  return request({ url: `/api/v1/knowledge/documents/${documentId}/favorite`, method: 'post' });
}

export function unfavoriteKbDocument(documentId: string) {
  return request({ url: `/api/v1/knowledge/documents/${documentId}/favorite`, method: 'delete' });
}

export function kbSocialStat(documentId: string): AxiosPromise<ApiEnvelope<KbSocialStatVO>> {
  return request({ url: `/api/v1/knowledge/documents/${documentId}/social`, method: 'get' });
}

export function listKbFavorite(): AxiosPromise<ApiEnvelope<string[]>> {
  return request({ url: '/api/v1/knowledge/favorites', method: 'get' });
}

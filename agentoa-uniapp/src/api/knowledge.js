import { get, post, del, unwrap } from '@/utils/request'

/** 知识库（docs/23 H5-H4-03，预览为只读，编辑留 PC） */
export function listSpaces(params) {
  return unwrap(get('/api/v1/knowledge/spaces', params))
}

export function listDocuments(params) {
  return unwrap(get('/api/v1/knowledge/documents', params))
}

export function searchDocuments(params) {
  return unwrap(get('/api/v1/knowledge/documents/search', params))
}

export function getDocument(id) {
  return unwrap(get(`/api/v1/knowledge/documents/${id}`))
}

export function listFavorites() {
  return unwrap(get('/api/v1/knowledge/favorites'))
}

export function favorite(documentId) {
  return unwrap(post(`/api/v1/knowledge/documents/${documentId}/favorite`))
}

export function unfavorite(documentId) {
  return unwrap(del(`/api/v1/knowledge/documents/${documentId}/favorite`))
}

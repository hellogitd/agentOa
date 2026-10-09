import { request, upload, unwrap, downloadAuthed, fetchImage } from '@/utils/request'

/** 私有文件上传（docs/23 H5-H2-02 / H5-H3-01）：→ {fileId, fileName, sizeBytes} */
export function uploadPrivateFile(filePath, fileName) {
  return unwrap(
    upload({
      url: '/api/v1/files',
      filePath,
      name: 'file',
      formData: fileName ? { fileName } : {}
    })
  )
}

/** AI 聊天图片附件（docs/23 H5-H6-03）：→ {fileId, fileName, contentType, sizeBytes} */
export function uploadAiAttachment(filePath) {
  return unwrap(
    upload({
      url: '/api/v1/ai/chat/attachments',
      filePath,
      name: 'file'
    })
  )
}

/** 流程表单附件下载（授权接口，docs/23 H5-H3-01） */
export function downloadFlowAttachment(instanceId, fileId, fileName) {
  return downloadAuthed(`/api/v1/wf/instances/${instanceId}/attachments/${fileId}/download`, fileName)
}

/** 本人上传的私有文件下载（草稿期预览） */
export function downloadPrivateFile(fileId, fileName) {
  return downloadAuthed(`/api/v1/files/${fileId}/download`, fileName)
}

/** 附件图片转 data URI（<image> 预览用） */
export function previewImage(fileId, mime = 'image/jpeg') {
  return fetchImage(`/api/v1/files/${fileId}/download`, mime)
}

export function previewFlowImage(instanceId, fileId, mime = 'image/jpeg') {
  return fetchImage(`/api/v1/wf/instances/${instanceId}/attachments/${fileId}/download`, mime)
}

/** 发票上传（docs/23 H5-H3-02）：fileId 先经 uploadPrivateFile 获得 */
export function createInvoice(data) {
  return unwrap(request({ url: '/api/v1/finance/invoices', method: 'POST', data, idempotent: true }))
}

export function getInvoice(id) {
  return unwrap(request({ url: `/api/v1/finance/invoices/${id}`, method: 'GET', silent: true }))
}

export function listInvoices(params) {
  return unwrap(request({ url: '/api/v1/finance/invoices', method: 'GET', data: params, silent: true }))
}

/** 发票图片预览（所有者/财务角色，docs/23 H5-H3-02） */
export function previewInvoiceImage(invoiceId, mime = 'image/jpeg') {
  return fetchImage(`/api/v1/finance/invoices/${invoiceId}/download`, mime)
}

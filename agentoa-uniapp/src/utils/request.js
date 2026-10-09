import { getToken, getTokenType, redirectToLogin } from './auth'
import { newIdempotencyKey, newRequestId } from './idempotency'
import { isH5 } from './platform'

const BASE_URL = String(import.meta.env.VITE_APP_BASE_API || '').replace(/\/+$/, '')
const CLIENT_ID = import.meta.env.VITE_APP_CLIENT_ID || ''

/** 相对路径补全 BASE_URL（流式/下载等旁路请求共用） */
export function resolveUrl(url) {
  return /^https?:\/\//.test(url) ? url : BASE_URL + url
}

/** /api/v1 统一响应封装（docs/05 1.3） */
export class ApiError extends Error {
  constructor(code, msg, data, statusCode) {
    super(msg || '请求失败')
    this.name = 'ApiError'
    this.code = code
    this.data = data
    this.statusCode = statusCode
  }
}

const WRITE_METHODS = ['POST', 'PUT', 'DELETE']

function normalizeQuery(data) {
  if (!data) return undefined
  const out = {}
  Object.keys(data).forEach((key) => {
    const value = data[key]
    if (value === undefined || value === null || value === '') return
    if (Array.isArray(value)) {
      if (value.length) out[key] = value.join(',')
      return
    }
    out[key] = value
  })
  return out
}

/**
 * 统一请求（docs/05 1.2/1.3/1.5/1.7）。
 * - 自动携带 Authorization、clientid、X-Request-Id
 * - 写操作可携带 Idempotency-Key（idempotent: true）
 * - HTTP 错误状态与 code 一致，解析信封后以 ApiError 抛出
 */
export function request(options) {
  const {
    url,
    method = 'GET',
    data,
    header = {},
    idempotent = false,
    auth = true,
    silent = false
  } = options

  const upper = String(method).toUpperCase()
  const isWrite = WRITE_METHODS.includes(upper)
  const token = getToken()
  if (auth && !token) {
    redirectToLogin()
    return Promise.reject(new ApiError(401, '未登录或登录状态已过期', null, 401))
  }
  const headers = {
    'Content-Type': 'application/json',
    'X-Request-Id': newRequestId(),
    ...header
  }
  if (CLIENT_ID) headers.clientid = CLIENT_ID
  if (auth && token) {
    headers.Authorization = `${getTokenType()} ${token}`
  }
  if (idempotent && isWrite && !headers['Idempotency-Key']) {
    headers['Idempotency-Key'] = newIdempotencyKey()
  }

  const payload = upper === 'GET' ? normalizeQuery(data) : data

  return new Promise((resolve, reject) => {
    uni.request({
      url: /^https?:\/\//.test(url) ? url : BASE_URL + url,
      method: /** @type {any} */ (upper),
      data: payload,
      header: headers,
      success: (res) => {
        const body = res.data
        // 文件/二进制或空体直接透传
        if (body === null || body === undefined || typeof body !== 'object' || body instanceof ArrayBuffer) {
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve(body)
          } else {
            reject(new ApiError(res.statusCode, `请求失败（HTTP ${res.statusCode}）`, body, res.statusCode))
          }
          return
        }
        const envelope = /** @type {{ code?: number, msg?: string, data?: any }} */ (body)
        const code = typeof envelope.code === 'number' ? envelope.code : res.statusCode
        if (code === 200) {
          resolve(body)
          return
        }
        if (code === 401) {
          redirectToLogin()
          reject(new ApiError(401, envelope.msg || '登录状态已过期，请重新登录', envelope.data, res.statusCode))
          return
        }
        if (!silent) {
          uni.showToast({ title: envelope.msg || `请求失败（${code}）`, icon: 'none', duration: 2500 })
        }
        reject(new ApiError(code, envelope.msg || '请求失败', envelope.data, res.statusCode))
      },
      fail: (err) => {
        if (!silent) {
          uni.showToast({ title: '网络异常，请稍后重试', icon: 'none', duration: 2500 })
        }
        reject(new ApiError(-1, err && err.errMsg ? err.errMsg : '网络异常', null, 0))
      }
    })
  })
}

export function get(url, data, options = {}) {
  return request({ url, method: 'GET', data, ...options })
}

export function post(url, data, options = {}) {
  return request({ url, method: 'POST', data, ...options })
}

export function put(url, data, options = {}) {
  return request({ url, method: 'PUT', data, ...options })
}

export function del(url, data, options = {}) {
  return request({ url, method: 'DELETE', data, ...options })
}

/** 解开 /api/v1 统一信封，返回 data 字段。 */
export function unwrap(promise) {
  return promise.then((res) => res && res.data !== undefined ? res.data : res)
}

/** arrayBuffer -> base64（H5 / 小程序通用）。 */
export function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer)
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
  let out = ''
  for (let i = 0; i < bytes.length; i += 3) {
    const b0 = bytes[i]
    const b1 = i + 1 < bytes.length ? bytes[i + 1] : 0
    const b2 = i + 2 < bytes.length ? bytes[i + 2] : 0
    out += chars[b0 >> 2]
    out += chars[((b0 & 3) << 4) | (b1 >> 4)]
    out += i + 1 < bytes.length ? chars[((b1 & 15) << 2) | (b2 >> 6)] : '='
    out += i + 2 < bytes.length ? chars[b2 & 63] : '='
  }
  return out
}

/**
 * 拉取需要鉴权的二进制资源（如流程图 PNG），返回 data URI。
 * 需要 responseType='arraybuffer'，`<image>` 无法携带 Authorization 头，因此走请求层转 base64。
 */
export function fetchImage(url, mime = 'image/png') {
  return new Promise((resolve, reject) => {
    const token = getToken()
    if (!token) {
      reject(new ApiError(401, '未登录或登录状态已过期', null, 401))
      return
    }
    const headers = {}
    if (CLIENT_ID) headers.clientid = CLIENT_ID
    headers.Authorization = `${getTokenType()} ${token}`
    uni.request({
      url: /^https?:\/\//.test(url) ? url : BASE_URL + url,
      method: 'GET',
      header: headers,
      responseType: 'arraybuffer',
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300 && res.data) {
          resolve(`data:${mime};base64,${arrayBufferToBase64(res.data)}`)
        } else {
          reject(new ApiError(res.statusCode || -1, '资源加载失败', null, res.statusCode))
        }
      },
      fail: (err) => reject(new ApiError(-1, err && err.errMsg ? err.errMsg : '资源加载失败', null, 0))
    })
  })
}

/**
 * multipart 文件上传（uni.uploadFile），鉴权/信封/幂等口径同 request。
 * options: { url, filePath, name='file', formData={}, header={}, idempotent=true, auth=true, silent=false }
 */
export function upload(options) {
  const { url, filePath, name = 'file', formData = {}, header = {}, idempotent = true, auth = true, silent = false } = options
  const token = getToken()
  if (auth && !token) {
    redirectToLogin()
    return Promise.reject(new ApiError(401, '未登录或登录状态已过期', null, 401))
  }
  const headers = { 'X-Request-Id': newRequestId(), ...header }
  if (CLIENT_ID) headers.clientid = CLIENT_ID
  if (auth && token) headers.Authorization = `${getTokenType()} ${token}`
  if (idempotent && !headers['Idempotency-Key']) headers['Idempotency-Key'] = newIdempotencyKey()
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: /^https?:\/\//.test(url) ? url : BASE_URL + url,
      filePath,
      name,
      formData,
      header: headers,
      success: (res) => {
        let body = res.data
        if (typeof body === 'string') {
          try {
            body = JSON.parse(body)
          } catch (e) {
            body = null
          }
        }
        if (!body || typeof body !== 'object') {
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve(body)
          } else {
            reject(new ApiError(res.statusCode, `上传失败（HTTP ${res.statusCode}）`, body, res.statusCode))
          }
          return
        }
        const envelope = /** @type {{ code?: number, msg?: string, data?: any }} */ (body)
        const code = typeof envelope.code === 'number' ? envelope.code : res.statusCode
        if (code === 200) {
          resolve(body)
          return
        }
        if (code === 401) {
          redirectToLogin()
          reject(new ApiError(401, envelope.msg || '登录状态已过期，请重新登录', envelope.data, res.statusCode))
          return
        }
        if (!silent) {
          uni.showToast({ title: envelope.msg || `上传失败（${code}）`, icon: 'none', duration: 2500 })
        }
        reject(new ApiError(code, envelope.msg || '上传失败', envelope.data, res.statusCode))
      },
      fail: (err) => {
        if (!silent) {
          uni.showToast({ title: '网络异常，上传失败', icon: 'none', duration: 2500 })
        }
        reject(new ApiError(-1, err && err.errMsg ? err.errMsg : '上传失败', null, 0))
      }
    })
  })
}

/**
 * 鉴权二进制下载（docs/23 H5-H3-01）：
 * H5 走 fetch blob 触发浏览器保存；小程序走 uni.downloadFile 返回临时路径（uni.openDocument 打开）。
 */
export function downloadAuthed(url, fileName) {
  const absUrl = /^https?:\/\//.test(url) ? url : BASE_URL + url
  const token = getToken()
  const headers = /** @type {Record<string, string>} */ ({})
  if (CLIENT_ID) headers.clientid = String(CLIENT_ID)
  if (token) headers.Authorization = `${getTokenType()} ${token}`
  if (isH5()) {
    return fetch(absUrl, { headers }).then((res) => {
      if (!res.ok) throw new ApiError(res.status, `下载失败（HTTP ${res.status}）`, null, res.status)
      return res.blob().then((blob) => {
        const link = document.createElement('a')
        link.href = URL.createObjectURL(blob)
        link.download = fileName || 'attachment'
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        setTimeout(() => URL.revokeObjectURL(link.href), 10000)
        return { saved: true }
      })
    })
  }
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: absUrl,
      header: headers,
      success: (res) => {
        if (res.statusCode === 200) {
          resolve({ tempFilePath: res.tempFilePath })
        } else {
          reject(new ApiError(res.statusCode, `下载失败（HTTP ${res.statusCode}）`, null, res.statusCode))
        }
      },
      fail: (err) => reject(new ApiError(-1, err && err.errMsg ? err.errMsg : '下载失败', null, 0))
    })
  })
}

export default { request, get, post, put, del, unwrap, fetchImage, upload, downloadAuthed, ApiError }

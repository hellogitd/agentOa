import { getToken, getTokenType } from './auth'
import { newRequestId } from './idempotency'
import { resolveUrl } from './request'
import { isH5 } from './platform'

/**
 * SSE 流式传输（docs/23 H5-H6-01）：
 * H5 用 fetch + ReadableStream；小程序无 fetch 流 → uni.request enableChunked + onChunkReceived
 * （未决项 1 定案，见 docs/23 §7）。两路统一按 `event: X` / `data: {...}` 帧回调 onEvent。
 * 返回 { done, abort }：done 为流结束 Promise（error 事件不 reject，由 onEvent 上抛给调用方）。
 */

function parseFrames(buffer) {
  const frames = []
  let rest = buffer
  let idx
  while ((idx = rest.indexOf('\n\n')) >= 0) {
    const raw = rest.slice(0, idx)
    rest = rest.slice(idx + 2)
    let event = 'message'
    let data = ''
    raw.split(/\r?\n/).forEach((line) => {
      const colon = line.indexOf(':')
      if (colon < 0) return
      const field = line.slice(0, colon).trim()
      const value = line.slice(colon + 1).trim()
      if (field === 'event') event = value
      else if (field === 'data') data += value
    })
    if (data) {
      let parsed = data
      try {
        parsed = JSON.parse(data)
      } catch (e) {
        // 非 JSON data 原样透传
      }
      frames.push({ event, data: parsed })
    }
  }
  return { frames, rest }
}

function authHeaders() {
  const headers = { 'Content-Type': 'application/json', 'X-Request-Id': newRequestId() }
  const token = getToken()
  if (token) headers.Authorization = `${getTokenType()} ${token}`
  return headers
}

export function streamSse({ url, method = 'POST', body, onEvent }) {
  const absUrl = resolveUrl(url)
  const headers = authHeaders()
  let aborted = false
  let cancel = () => {}

  const done = (async () => {
    if (isH5()) {
      const controller = typeof AbortController !== 'undefined' ? new AbortController() : null
      cancel = () => {
        aborted = true
        if (controller) controller.abort()
      }
      const res = await fetch(absUrl, {
        method,
        headers,
        body: body == null ? undefined : JSON.stringify(body),
        signal: controller ? controller.signal : undefined
      })
      if (!res.ok) {
        let payload = null
        try {
          payload = await res.json()
        } catch (e) {
          // 400/500 可能是纯文本
        }
        onEvent({ event: 'error', data: (payload && payload.msg) ? payload : { code: String(res.status), msg: `请求失败（HTTP ${res.status}）` } })
        return
      }
      const reader = res.body && res.body.getReader ? res.body.getReader() : null
      if (!reader) {
        onEvent({ event: 'error', data: { code: 'STREAM_UNSUPPORTED', msg: '当前环境不支持流式输出' } })
        return
      }
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      for (;;) {
        const chunk = await reader.read()
        if (chunk.done) break
        buffer += decoder.decode(chunk.value, { stream: true })
        const { frames, rest } = parseFrames(buffer)
        buffer = rest
        frames.forEach((f) => onEvent(f))
      }
      return
    }

    // 小程序：chunked 分段回调
    await new Promise((resolve) => {
      let buffer = ''
      const task = /** @type {any} */ (
        uni.request({
          url: absUrl,
          method: /** @type {any} */ (method),
          data: body == null ? undefined : body,
          header: headers,
          enableChunked: true,
          success: () => resolve(),
          fail: () => {
            if (!aborted) {
              onEvent({ event: 'error', data: { code: 'NETWORK_ERROR', msg: '网络异常，流式连接失败' } })
            }
            resolve()
          },
          complete: () => resolve()
        })
      )
      cancel = () => {
        aborted = true
        try {
          task.abort()
        } catch (e) {
          // ignore
        }
        resolve()
      }
      if (task && task.onChunkReceived) {
        task.onChunkReceived((res) => {
          const text = decodeChunk(res.data)
          if (!text) return
          buffer += text
          const { frames, rest } = parseFrames(buffer)
          buffer = rest
          frames.forEach((f) => onEvent(f))
        })
      } else {
        onEvent({ event: 'error', data: { code: 'STREAM_UNSUPPORTED', msg: '当前环境不支持流式输出，请升级基础库' } })
        try {
          task.abort()
        } catch (e) {
          // ignore
        }
        resolve()
      }
    })
  })()

  return {
    done,
    abort() {
      if (!aborted) cancel()
    }
  }
}

function decodeChunk(data) {
  if (typeof data === 'string') return data
  if (data instanceof ArrayBuffer) {
    try {
      return new TextDecoder('utf-8').decode(new Uint8Array(data))
    } catch (e) {
      return ''
    }
  }
  return ''
}

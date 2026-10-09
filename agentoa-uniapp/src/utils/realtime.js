import { reactive } from 'vue'
import { post, unwrap } from '@/utils/request'
import { getToken } from '@/utils/auth'

/**
 * 站内 ticket WebSocket 实时通道（docs/23 H5-H5-01/03）：
 * 短时单次 ticket（/api/v1/auth/ws-ticket）→ /ws?ticket= ；心跳 30s 带应答跟踪（假死即断开重连）；
 * 指数退避重连 1s→2s→…→30s 封顶；切后台回前台由 App.onShow 触发 connectRealtime 补偿。
 * 推送载荷：{ receiverId, messageId, type, title }（type: TODO|NOTICE|MENTION|SYSTEM）。
 */

const state = reactive({ status: 'disconnected', lastMessage: null })
const listeners = new Set()

let socketTask = null
let heartbeatTimer = null
let reconnectTimer = null
let backoffMs = 1000
let awaitingAck = false
let manuallyClosed = false

const HEARTBEAT_INTERVAL = 30 * 1000
const BACKOFF_MAX = 30 * 1000

function wsUrl(ticket) {
  const configured = String(import.meta.env.VITE_APP_WS_URL || '').trim()
  if (configured) {
    return configured.replace(/\/+$/, '') + '/ws?ticket=' + encodeURIComponent(ticket)
  }
  if (typeof location !== 'undefined' && location.host) {
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${proto}//${location.host}/ws?ticket=${encodeURIComponent(ticket)}`
  }
  const base = String(import.meta.env.VITE_APP_BASE_API || '').replace(/^http/, 'ws').replace(/\/+$/, '')
  return base + '/ws?ticket=' + encodeURIComponent(ticket)
}

function parseMessage(raw) {
  if (typeof raw !== 'string') return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}

function startHeartbeat() {
  stopHeartbeat()
  awaitingAck = false
  heartbeatTimer = setInterval(() => {
    if (!socketTask) return
    if (awaitingAck) {
      // 上一次心跳未应答：连接假死，主动断开触发重连降级
      closeSocket()
      return
    }
    awaitingAck = true
    try {
      socketTask.send({ data: JSON.stringify({ type: 'HEARTBEAT' }) })
    } catch (e) {
      closeSocket()
    }
  }, HEARTBEAT_INTERVAL)
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
  awaitingAck = false
}

function scheduleReconnect() {
  if (manuallyClosed || reconnectTimer || !getToken()) return
  const delay = backoffMs
  backoffMs = Math.min(backoffMs * 2, BACKOFF_MAX)
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connectRealtime()
  }, delay)
}

function closeSocket() {
  stopHeartbeat()
  const task = socketTask
  socketTask = null
  if (task) {
    try {
      task.close({})
    } catch (e) {
      // ignore
    }
  }
  state.status = 'disconnected'
  scheduleReconnect()
}

export function getRealtimeState() {
  return state
}

/** 订阅推送消息；返回取消订阅函数 */
export function onRealtimeMessage(fn) {
  listeners.add(fn)
  return () => listeners.delete(fn)
}

export async function connectRealtime() {
  if (!getToken()) return
  if (socketTask || state.status === 'connecting') return
  manuallyClosed = false
  state.status = 'connecting'
  try {
    const ticket = await unwrap(post('/api/v1/auth/ws-ticket', {}, { silent: true }))
    if (!ticket || !ticket.ticket) throw new Error('no ws ticket')
    socketTask = uni.connectSocket({
      url: wsUrl(ticket.ticket),
      complete: () => {}
    })
    socketTask.onOpen(() => {
      state.status = 'connected'
      backoffMs = 1000
      startHeartbeat()
    })
    socketTask.onMessage((res) => {
      const msg = parseMessage(res.data)
      if (!msg) return
      if (msg.type === 'HEARTBEAT') {
        awaitingAck = false
        return
      }
      state.lastMessage = msg
      listeners.forEach((fn) => {
        try {
          fn(msg)
        } catch (e) {
          // 单个订阅者异常不影响其它订阅者
        }
      })
    })
    socketTask.onClose(() => {
      if (socketTask) closeSocket()
    })
    socketTask.onError(() => {
      if (socketTask) closeSocket()
    })
  } catch (e) {
    socketTask = null
    state.status = 'disconnected'
    scheduleReconnect()
  }
}

export function disconnectRealtime() {
  manuallyClosed = true
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  backoffMs = 1000
  closeSocket()
  manuallyClosed = true
  state.status = 'disconnected'
}

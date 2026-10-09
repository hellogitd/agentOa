import { getToken } from '@/utils/auth';
import { ElNotification } from 'element-plus';
import { useNoticeStore } from '@/store/modules/notice';

/**
 * 站内实时通道（docs/22 FE-F3-02/03）：ticket WebSocket（/backend/ws）统一收口。
 * <p>
 * 与页面拉取双轨互补：推送仅触发去重入列与未读刷新，角标始终以接口拉取为准；
 * 断线按指数退避自动重连（1s→2s→…→30s 封顶），期间 store 降级为定时拉取。
 */

const MAX_BACKOFF_MS = 30_000;
const HEARTBEAT_MS = 30_000;

let socket: WebSocket | undefined;
let heartbeat: ReturnType<typeof setInterval> | undefined;
let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
let started = false;
let disposed = false;

const wsUrl = (ticket: string) => {
  const url = new URL(`${import.meta.env.VITE_APP_BASE_API}/ws`, location.href);
  url.protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  url.searchParams.set('ticket', ticket);
  return url.toString();
};

const scheduleReconnect = () => {
  const store = useNoticeStore();
  store.recordRetry();
  store.setConnection('disconnected');
  const delay = Math.min(1000 * 2 ** Math.min(store.connection.attempts, 5), MAX_BACKOFF_MS);
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
  }
  reconnectTimer = setTimeout(() => {
    void open();
  }, delay);
};

const open = async () => {
  const store = useNoticeStore();
  if (disposed || !getToken()) {
    return;
  }
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    return;
  }
  store.setConnection('connecting');
  try {
    // 后台换票走轻量 fetch：不触发请求层全局 loading/错误弹窗
    const response = await fetch(import.meta.env.VITE_APP_BASE_API + '/api/v1/auth/ws-ticket', {
      method: 'POST',
      headers: {
        Authorization: 'Bearer ' + getToken(),
        clientid: import.meta.env.VITE_APP_CLIENT_ID,
        'Content-Type': 'application/json'
      },
      body: '{}'
    });
    const res: any = response.ok ? await response.json() : null;
    const ticket = res?.data?.ticket;
    if (disposed) {
      return;
    }
    if (!ticket) {
      // 换票失败（未登录/会话失效）：不再空转重连，保留拉取兜底
      store.setConnection('disconnected', '实时连接票据获取失败');
      return;
    }
    const ws = new WebSocket(wsUrl(ticket));
    socket = ws;
    // 心跳应答跟踪：服务端收到 HEARTBEAT 会回显，超时未应答即判定连接假死（如断网后套接字半开）
    let awaitingEcho = false;

    ws.onopen = () => {
      if (disposed) {
        ws.close();
        return;
      }
      store.setConnection('connected');
      void store.refreshUnread();
      if (heartbeat) {
        clearInterval(heartbeat);
      }
      heartbeat = setInterval(() => {
        if (ws.readyState !== WebSocket.OPEN) {
          return;
        }
        if (awaitingEcho) {
          // 上一次心跳无应答：主动断开触发重连（断网场景退化为定时拉取，docs/22 FE-F3-03）
          ws.close();
          return;
        }
        awaitingEcho = true;
        try {
          ws.send(JSON.stringify({ type: 'HEARTBEAT' }));
        } catch {
          ws.close();
        }
      }, HEARTBEAT_MS);
    };

    ws.onmessage = (event) => {
      awaitingEcho = false;
      const fresh = store.handlePush(event.data);
      if (!fresh) {
        return;
      }
      let payload: any;
      try {
        payload = JSON.parse(event.data);
      } catch {
        payload = { title: event.data };
      }
      if (payload?.type === 'HEARTBEAT') {
        return;
      }
      ElNotification({
        title: '消息',
        message: payload?.title || '您有一条新消息',
        type: 'success',
        duration: 3000
      });
    };

    ws.onerror = () => {
      // onclose 统一处理重连
    };

    ws.onclose = () => {
      if (heartbeat) {
        clearInterval(heartbeat);
        heartbeat = undefined;
      }
      if (socket === ws) {
        socket = undefined;
      }
      if (disposed) {
        return;
      }
      scheduleReconnect();
    };
  } catch (error: any) {
    store.setConnection('disconnected', error?.message ?? '实时连接失败');
    scheduleReconnect();
  }
};

/** 启动实时连接（幂等；未登录不连接） */
export const connectRealtime = () => {
  if (started) {
    return;
  }
  started = true;
  disposed = false;
  void open();
};

/** 停止实时连接（登出/刷新时调用） */
export const disconnectRealtime = () => {
  disposed = true;
  started = false;
  if (heartbeat) {
    clearInterval(heartbeat);
    heartbeat = undefined;
  }
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = undefined;
  }
  if (socket) {
    socket.close();
    socket = undefined;
  }
  useNoticeStore().setConnection('disconnected');
};

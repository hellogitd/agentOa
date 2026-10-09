import { getToken } from '@/utils/auth';
import { ElNotification } from 'element-plus';
import { useNoticeStore } from '@/store/modules/notice';

/**
 * 上游 WebSocket 通道（/resource/websocket，部署可选，docs/22 FE-F3-01 开关矩阵）。
 * 消息统一经 notice store 去重入列，未读角标以拉取为准。
 */
export const initWebSocket = (url: any) => {
  if (import.meta.env.VITE_APP_WEBSOCKET === 'false') {
    return;
  }
  const noticeStore = useNoticeStore();
  url = url + '?Authorization=Bearer ' + getToken() + '&clientid=' + import.meta.env.VITE_APP_CLIENT_ID;
  useWebSocket(url, {
    autoReconnect: {
      // 重连最大次数
      retries: 10,
      // 重连间隔（通道级退避由 realtime.ts 的 ticket 通道提供，此处保持上游口径）
      delay: 3000,
      onFailed() {
        noticeStore.setConnection('disconnected', 'WebSocket 重连失败');
      }
    },
    heartbeat: {
      message: JSON.stringify({ type: 'ping' }),
      // 发送心跳的间隔
      interval: 10000,
      // 接收到心跳response的超时时间
      pongTimeout: 2000
    },
    onConnected() {
      noticeStore.setConnection('connected');
    },
    onDisconnected() {
      noticeStore.setConnection('disconnected');
    },
    onMessage: (_, e) => {
      if (e.data.indexOf('ping') > 0) {
        return;
      }
      const fresh = noticeStore.handlePush(e.data);
      if (fresh) {
        ElNotification({
          title: '消息',
          message: e.data,
          type: 'success',
          duration: 3000
        });
      }
    }
  });
};

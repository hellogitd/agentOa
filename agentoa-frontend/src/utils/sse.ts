import { getToken } from '@/utils/auth';
import { ElNotification } from 'element-plus';
import { useNoticeStore } from '@/store/modules/notice';

/**
 * 上游 SSE 通道（/resource/sse，部署可选，docs/22 FE-F3-01 开关矩阵）。
 * 消息统一经 notice store 去重入列，未读角标以拉取为准。
 */
export const initSSE = (url: any) => {
  if (import.meta.env.VITE_APP_SSE === 'false') {
    return;
  }

  const noticeStore = useNoticeStore();
  url = url + '?Authorization=Bearer ' + getToken() + '&clientid=' + import.meta.env.VITE_APP_CLIENT_ID;
  const { status, data, error } = useEventSource(url, [], {
    autoReconnect: {
      retries: 10,
      delay: 5000,
      onFailed() {
        noticeStore.setConnection('disconnected', 'SSE 重连失败');
      }
    }
  });

  watch(status, () => {
    if (status.value === 'OPEN') {
      noticeStore.setConnection('connected');
    } else if (status.value === 'CLOSED') {
      noticeStore.setConnection('disconnected');
    } else {
      noticeStore.setConnection('connecting');
    }
  });

  watch(error, () => {
    if (error.value) {
      noticeStore.setConnection('disconnected', String(error.value));
    }
    error.value = null;
  });

  watch(data, () => {
    if (!data.value) return;
    const fresh = noticeStore.handlePush(data.value);
    if (fresh) {
      ElNotification({
        title: '消息',
        message: data.value,
        type: 'success',
        duration: 3000
      });
    }
    data.value = null;
  });
};

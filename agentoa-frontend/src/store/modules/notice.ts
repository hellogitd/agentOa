import { defineStore } from 'pinia';
import { computed, reactive, ref } from 'vue';
import { unreadCount, UnreadCountVO } from '@/api/notice';

export interface NoticeItem {
  id?: string;
  title?: string;
  read: boolean;
  message: any;
  time: string;
}

/** 实时连接状态（docs/22 FE-F3-03）：disabled=通道未启用，connecting/connected/disconnected */
export type RealtimeStatus = 'disabled' | 'connecting' | 'connected' | 'disconnected';

/**
 * 站内通知中枢（docs/22 FE-F3-02/03）：
 * - 推送事件（WS/SSE）经 {@link handlePush} 入列并按 messageId 去重；
 * - 未读角标以接口拉取为准（{@link refreshUnread}），推送仅作为触发器，双轨不重复计数；
 * - 连接状态与重连节奏由 utils/realtime 上报，断线期间降级为定时拉取。
 */
export const useNoticeStore = defineStore('notice', () => {
  const state = reactive({
    notices: [] as NoticeItem[]
  });

  const unread = ref<UnreadCountVO>({ total: 0, todo: 0, notice: 0, mention: 0, system: 0 });
  const connection = reactive<{
    status: RealtimeStatus;
    attempts: number;
    lastError: string;
    lastEventAt: number;
  }>({
    status: 'disabled',
    attempts: 0,
    lastError: '',
    lastEventAt: 0
  });

  /** 推送去重表（messageId → 已入列） */
  const seen = new Set<string>();
  let unreadTimer: ReturnType<typeof setInterval> | undefined;

  const unreadTotal = computed(() => unread.value.total ?? 0);
  const hasUnread = computed(() => unreadTotal.value > 0);

  const addNotice = (notice: NoticeItem) => {
    if (notice.id != null && notice.id !== '') {
      if (seen.has(String(notice.id))) {
        return false;
      }
      seen.add(String(notice.id));
    }
    state.notices.unshift(notice);
    if (state.notices.length > 50) {
      state.notices.pop();
    }
    return true;
  };

  /**
   * 处理一条推送事件（WS/SSE 载荷）：去重入列 + 刷新未读角标 + 记录到达时间。
   * @returns true=新消息（调用方可弹通知），false=重复/无关事件
   */
  const handlePush = (payload: any): boolean => {
    connection.lastEventAt = Date.now();
    let data = payload;
    if (typeof payload === 'string') {
      try {
        data = JSON.parse(payload);
      } catch {
        data = { message: payload };
      }
    }
    if (data && data.type === 'HEARTBEAT') {
      return false;
    }
    const id = data?.messageId != null ? String(data.messageId) : undefined;
    const title = data?.title || data?.message || '新消息';
    const fresh = addNotice({
      id,
      title,
      read: false,
      message: data?.message ?? data?.title ?? payload,
      time: new Date().toLocaleString()
    });
    void refreshUnread();
    return fresh;
  };

  /** 以接口结果为准刷新未读角标（拉取轨） */
  const refreshUnread = async () => {
    try {
      const res: any = await unreadCount();
      unread.value = res.data ?? { total: 0, todo: 0, notice: 0, mention: 0, system: 0 };
    } catch {
      // 拉取失败保留旧值（断线降级期间不打断主流程）
    }
  };

  const markLocalRead = (notice: NoticeItem) => {
    notice.read = true;
  };

  const readAll = () => {
    state.notices.forEach((item: NoticeItem) => {
      item.read = true;
    });
  };

  const removeNotice = (notice: NoticeItem) => {
    state.notices.splice(state.notices.indexOf(notice), 1);
  };

  const clearNotice = () => {
    state.notices = [];
    seen.clear();
    unread.value = { total: 0, todo: 0, notice: 0, mention: 0, system: 0 };
  };

  const setConnection = (status: RealtimeStatus, error?: string) => {
    connection.status = status;
    if (status === 'connected') {
      connection.attempts = 0;
      connection.lastError = '';
      stopFallbackPoll();
    } else if (status === 'disconnected') {
      connection.lastError = error ?? connection.lastError;
      startFallbackPoll();
    }
  };

  const recordRetry = () => {
    connection.attempts += 1;
  };

  /** 断线降级：每 60s 拉取一次未读（docs/22 FE-F3-03「断线期间降级为拉取」） */
  const startFallbackPoll = () => {
    if (unreadTimer) {
      return;
    }
    unreadTimer = setInterval(() => {
      void refreshUnread();
    }, 60_000);
    void refreshUnread();
  };

  const stopFallbackPoll = () => {
    if (unreadTimer) {
      clearInterval(unreadTimer);
      unreadTimer = undefined;
    }
  };

  return {
    state,
    unread,
    connection,
    unreadTotal,
    hasUnread,
    addNotice,
    handlePush,
    refreshUnread,
    markLocalRead,
    readAll,
    removeNotice,
    clearNotice,
    setConnection,
    recordRetry,
    startFallbackPoll,
    stopFallbackPoll
  };
});

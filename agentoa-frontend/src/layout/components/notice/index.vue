<template>
  <el-popover placement="bottom-end" :width="320" trigger="click" popper-class="notice-popper" @show="onShow">
    <template #reference>
      <el-badge :value="noticeStore.unreadTotal" :hidden="!noticeStore.hasUnread" :max="99" class="notice-badge">
        <el-icon :size="18" class="notice-bell"><Bell /></el-icon>
      </el-badge>
    </template>

    <div class="notice-panel">
      <div class="head-box">
        <div class="head-box-title">
          消息通知
          <el-tag v-if="noticeStore.connection.status !== 'connected'" size="small" type="info" class="status-tag">
            {{ statusText }}
          </el-tag>
        </div>
        <div class="head-box-btn" @click="handleReadAll">全部已读</div>
      </div>

      <div class="content-box">
        <template v-if="noticeStore.state.notices.length > 0">
          <div
            v-for="(item, index) in noticeStore.state.notices"
            :key="item.id ?? index"
            class="content-box-item"
            @click="noticeStore.markLocalRead(item)"
          >
            <div class="item-content">
              <div class="item-title">{{ item.title || item.message }}</div>
              <div class="content-box-time">{{ item.time }}</div>
            </div>
            <span v-if="item.read" class="el-tag el-tag--success el-tag--mini read">已读</span>
            <span v-else class="el-tag el-tag--danger el-tag--mini read">未读</span>
          </div>
        </template>
        <el-empty
          v-else
          :description="noticeStore.connection.status === 'connected' ? '暂无消息' : '实时通道未连接，已降级为定时拉取'"
          :image-size="60"
        />
      </div>

      <div class="foot-box" @click="goMessageCenter">进入消息中心</div>
    </div>
  </el-popover>
</template>

<script setup lang="ts" name="LayoutNoticePanel">
import { ElMessage } from 'element-plus';
import { markAllRead } from '@/api/notice';
import { useNoticeStore } from '@/store/modules/notice';

const router = useRouter();
const noticeStore = useNoticeStore();

const statusText = computed(() => {
  switch (noticeStore.connection.status) {
    case 'connecting':
      return '连接中';
    case 'disconnected':
      return '断线重连中';
    case 'disabled':
      return '定时拉取';
    default:
      return '';
  }
});

const onShow = () => {
  void noticeStore.refreshUnread();
};

const handleReadAll = async () => {
  try {
    await markAllRead();
    noticeStore.readAll();
    await noticeStore.refreshUnread();
    ElMessage.success('已全部标记为已读');
  } catch {
    // 失败提示由 request 层统一弹出
  }
};

const goMessageCenter = () => {
  void router.push('/notice/message');
};
</script>

<style lang="scss" scoped>
.notice-badge {
  display: flex;
  align-items: center;
  :deep(.el-badge__content) {
    transform: translateY(2px) translateX(100%);
  }
}
.notice-bell {
  cursor: pointer;
  color: var(--el-text-color-primary);
  opacity: 0.8;
  &:hover {
    opacity: 1;
    color: var(--el-color-primary);
  }
}
.notice-panel {
  .head-box {
    display: flex;
    border-bottom: 1px solid var(--el-border-color-lighter);
    box-sizing: border-box;
    color: var(--el-text-color-primary);
    justify-content: space-between;
    height: 35px;
    align-items: center;
    .head-box-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-weight: 600;
      .status-tag {
        font-weight: 400;
      }
    }
    .head-box-btn {
      color: var(--el-color-primary);
      font-size: 13px;
      cursor: pointer;
      opacity: 0.8;
      &:hover {
        opacity: 1;
      }
    }
  }
  .content-box {
    max-height: 300px;
    overflow: auto;
    font-size: 13px;
    .content-box-item {
      padding-top: 12px;
      display: flex;
      align-items: flex-start;
      gap: 8px;
      cursor: pointer;
      &:last-of-type {
        padding-bottom: 12px;
      }
      .item-content {
        width: 100%;
        display: flex;
        flex-direction: column;
      }
      .item-title {
        color: var(--el-text-color-primary);
        overflow: hidden;
        text-overflow: ellipsis;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
      }
      .content-box-time {
        color: var(--el-text-color-secondary);
        margin-top: 4px;
      }
      .read {
        flex-shrink: 0;
        margin-top: 2px;
      }
    }
  }
  .foot-box {
    height: 35px;
    color: var(--el-color-primary);
    font-size: 13px;
    cursor: pointer;
    opacity: 0.8;
    display: flex;
    align-items: center;
    justify-content: center;
    border-top: 1px solid var(--el-border-color-lighter);
    &:hover {
      opacity: 1;
    }
  }
  :deep(.el-empty__description p) {
    font-size: 13px;
  }
}
</style>

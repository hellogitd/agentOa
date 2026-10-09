<template>
  <view class="tk">
    <view class="tk__tabs">
      <view
        v-for="t in tabs"
        :key="t.key"
        class="tk__tab"
        :class="{ 'tk__tab--active': status === t.key }"
        @click="switchTab(t.key)"
      >
        <text>{{ t.label }}</text>
      </view>
    </view>

    <view v-if="loading && list.length === 0" class="tk__state">加载中…</view>
    <oa-empty v-else-if="list.length === 0" text="暂无任务" hint="任务由 PC 端协同模块创建分派" />

    <view v-for="item in list" :key="item.id" class="tk__card">
      <view class="tk__card-head">
        <text class="tk__card-title">{{ item.title }}</text>
        <oa-tag :text="statusOf(item.status).label" :tone="statusOf(item.status).tone" />
      </view>
      <view class="tk__row" v-if="item.dueDate">
        <text class="tk__row-label">截止</text>
        <text class="tk__row-value">{{ fmtDate(item.dueDate) }}</text>
      </view>
      <view class="tk__row">
        <text class="tk__row-label">发起</text>
        <text class="tk__row-value">{{ item.assignerName || '-' }} · 优先级 {{ item.priority || '-' }}</text>
      </view>
      <view class="tk__row" v-if="item.progress != null">
        <text class="tk__row-label">进度</text>
        <text class="tk__row-value">{{ item.progress }}%</text>
      </view>
      <view v-if="canEdit && !isTerminal(item.status)" class="tk__acts">
        <view v-if="item.status === 1" class="tk__act" @click="transition(item, '2')">开始</view>
        <view v-if="item.status === 3" class="tk__act" @click="transition(item, '2')">解除受阻</view>
        <view v-if="item.status !== 4 && item.status !== 5" class="tk__act tk__act--ok" @click="transition(item, '4')">完成</view>
        <view v-if="item.status !== 5" class="tk__act" @click="transition(item, '5')">取消</view>
      </view>
    </view>

    <view v-if="list.length" class="tk__more">
      <text v-if="hasMore" @click="loadMore">加载更多</text>
      <text v-else>没有更多了</text>
    </view>
  </view>
</template>

<script>
import { listTasks, changeTaskStatus, taskStatus } from '@/api/task'
import { formatDate } from '@/utils/format'
import { hasPermission } from '@/utils/auth'
import OaEmpty from '@/components/oa-empty.vue'
import OaTag from '@/components/oa-tag.vue'

const PAGE_SIZE = 20

export default {
  components: { OaEmpty, OaTag },
  data() {
    return {
      status: '',
      tabs: [
        { key: '', label: '全部' },
        { key: '1', label: '待办' },
        { key: '2', label: '进行中' },
        { key: '4', label: '已完成' }
      ],
      list: [],
      pageNum: 1,
      total: 0,
      loading: false,
      finished: false
    }
  },
  computed: {
    hasMore() {
      return !this.finished && this.list.length < this.total
    },
    canEdit() {
      return hasPermission('cl:task:edit')
    }
  },
  onShow() {
    this.reload()
  },
  onPullDownRefresh() {
    this.reload().finally(() => uni.stopPullDownRefresh())
  },
  onReachBottom() {
    if (this.hasMore) this.loadMore()
  },
  methods: {
    fmtDate: formatDate,
    statusOf(s) {
      return taskStatus(s)
    },
    isTerminal(s) {
      return Number(s) === 4 || Number(s) === 5
    },
    switchTab(key) {
      this.status = key
      this.reload()
    },
    reload() {
      this.pageNum = 1
      this.list = []
      this.finished = false
      return this.fetch()
    },
    loadMore() {
      if (this.loading || this.finished) return
      this.pageNum += 1
      this.fetch()
    },
    async fetch() {
      this.loading = true
      try {
        const page = await listTasks({
          scope: 'self',
          status: this.status || undefined,
          pageNum: this.pageNum,
          pageSize: PAGE_SIZE
        })
        const records = page.records || []
        this.list = this.pageNum === 1 ? records : this.list.concat(records)
        this.total = Number(page.total) || 0
        this.finished = this.list.length >= this.total
      } catch (e) {
        this.finished = true
      } finally {
        this.loading = false
      }
    },
    async transition(item, status) {
      try {
        await changeTaskStatus(item.id, { lockVersion: item.lockVersion, status })
        uni.showToast({ title: '已更新', icon: 'success' })
        this.reload()
      } catch (e) {
        // 状态机冲突/权限错误信封由请求层提示
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.tk {
  padding-bottom: 40rpx;
}
.tk__tabs {
  display: flex;
  padding: 16rpx 24rpx;
}
.tk__tab {
  padding: 12rpx 28rpx;
  margin-right: 16rpx;
  background: #f0f2f7;
  border-radius: 28rpx;
  font-size: 24rpx;
  color: $oa-text-secondary;
}
.tk__tab--active {
  background: $oa-primary;
  color: #ffffff;
}
.tk__state {
  padding: 40rpx;
  text-align: center;
  color: $oa-text-muted;
  font-size: 26rpx;
}
.tk__card {
  margin: 16rpx 24rpx;
  padding: 22rpx 24rpx;
  background: #ffffff;
  border-radius: 16rpx;
}
.tk__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.tk__card-title {
  flex: 1;
  font-size: 28rpx;
  font-weight: 600;
  color: $oa-text;
}
.tk__row {
  display: flex;
  padding: 6rpx 0;
}
.tk__row-label {
  width: 90rpx;
  font-size: 22rpx;
  color: $oa-text-secondary;
}
.tk__row-value {
  flex: 1;
  font-size: 22rpx;
  color: $oa-text;
}
.tk__acts {
  margin-top: 12rpx;
  display: flex;
  justify-content: flex-end;
}
.tk__act {
  margin-left: 16rpx;
  padding: 10rpx 30rpx;
  border-radius: 28rpx;
  font-size: 24rpx;
  color: $oa-text-secondary;
  background: #f0f2f7;
}
.tk__act--ok {
  color: #ffffff;
  background: $oa-primary;
}
.tk__more {
  padding: 24rpx 0 40rpx;
  text-align: center;
  font-size: 22rpx;
  color: $oa-text-muted;
}
</style>

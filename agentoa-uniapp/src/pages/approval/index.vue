<template>
  <view class="ap">
    <view class="ap__tabs">
      <view
        v-for="t in tabs"
        :key="t.key"
        class="ap__tab"
        :class="{ 'ap__tab--active': tab === t.key }"
        @click="switchTab(t.key)"
      >
        <text>{{ t.label }}</text>
        <text v-if="t.key === 'todo' && todoTotal" class="ap__tab-count">{{ todoTotal }}</text>
      </view>
    </view>

    <view class="ap__create" @click="goApply">＋ 发起新申请</view>

    <view class="ap__list">
      <view v-if="loading && list.length === 0" class="ap__state">加载中…</view>
      <oa-empty v-else-if="list.length === 0" :text="emptyText" hint="下拉可刷新" />

      <view v-for="item in list" :key="keyOf(item)" class="ap__card" @click="openDetail(item)">
        <view class="ap__card-head">
          <text class="ap__card-title">{{ item.title }}</text>
          <oa-tag :text="statusOf(item).label" :tone="statusOf(item).tone" />
        </view>
        <view class="ap__card-body">
          <text class="ap__card-meta">{{ bizName(item.businessType) }}</text>
          <text class="ap__card-meta">{{ tab === 'started' ? '发起于 ' : tab === 'done' ? '办理于 ' : '待我办理：' }}{{ timeOf(item) }}</text>
        </view>
        <view class="ap__card-foot" v-if="tab === 'todo'">
          <text class="ap__card-task">{{ item.taskName || '待办任务' }}</text>
          <text class="ap__card-action">去办理 ›</text>
        </view>
        <view class="ap__card-foot" v-else-if="tab === 'started'">
          <text class="ap__card-task">{{ item.currentTaskName ? '当前节点：' + item.currentTaskName : statusOf(item).label }}</text>
          <text class="ap__card-action">查看详情 ›</text>
        </view>
      </view>

      <view v-if="list.length" class="ap__more">
        <text v-if="hasMore" @click="loadMore">加载更多</text>
        <text v-else>没有更多了</text>
      </view>
    </view>
  </view>
</template>

<script>
import { listTodo, listDone, listInstances, instanceStatus, BUSINESS_TYPE_NAME } from '@/api/workflow'
import { formatDateTime, formatDate } from '@/utils/format'
import { go as navGo, consumeApprovalTab } from '@/utils/nav'
import OaEmpty from '@/components/oa-empty.vue'
import OaTag from '@/components/oa-tag.vue'

const PAGE_SIZE = 20

export default {
  components: { OaEmpty, OaTag },
  data() {
    return {
      tabs: [
        { key: 'todo', label: '待办' },
        { key: 'done', label: '已办' },
        { key: 'started', label: '我发起' }
      ],
      tab: 'todo',
      list: [],
      pageNum: 1,
      total: 0,
      loading: false,
      finished: false
    }
  },
  computed: {
    todoTotal() {
      return this.tab === 'todo' ? this.total : 0
    },
    hasMore() {
      return !this.finished && this.list.length < this.total
    },
    emptyText() {
      return this.tab === 'todo' ? '暂无待办' : this.tab === 'done' ? '暂无已办' : '暂无我发起的流程'
    }
  },
  onLoad(options) {
    if (options && options.tab && this.tabs.some((t) => t.key === options.tab)) {
      this.tab = options.tab
    }
  },
  onShow() {
    const pending = consumeApprovalTab()
    if (pending && this.tabs.some((t) => t.key === pending) && pending !== this.tab) {
      this.tab = pending
    }
    this.reload()
  },
  onPullDownRefresh() {
    this.reload().finally(() => uni.stopPullDownRefresh())
  },
  onReachBottom() {
    if (this.hasMore) this.loadMore()
  },
  methods: {
    bizName(type) {
      return BUSINESS_TYPE_NAME[type] || type || '-'
    },
    keyOf(item) {
      return item.taskId || item.id
    },
    statusOf(item) {
      return this.tab === 'started' ? instanceStatus(item.status) : instanceStatus(item.instanceStatus)
    },
    timeOf(item) {
      if (this.tab === 'todo') return formatDateTime(item.createTime) || '-'
      if (this.tab === 'done') return formatDateTime(item.endTime) || '-'
      return formatDate(item.startTime) || '-'
    },
    switchTab(key) {
      if (this.tab === key) return
      this.tab = key
      this.reload()
    },
    openDetail(item) {
      const id = item.instanceId || item.id
      if (!id) return
      navGo(`/pages/approval/detail?id=${id}&task=${item.taskId || ''}`)
    },
    goApply() {
      navGo('/pages/approval/apply')
    },
    async reload() {
      this.pageNum = 1
      this.list = []
      this.finished = false
      await this.fetch()
    },
    loadMore() {
      if (this.loading || this.finished) return
      this.pageNum += 1
      this.fetch()
    },
    async fetch() {
      this.loading = true
      try {
        const params = { pageNum: this.pageNum, pageSize: PAGE_SIZE }
        let records = []
        let total = 0
        if (this.tab === 'todo') {
          const page = await listTodo(params)
          records = page.records || []
          total = Number(page.total) || 0
        } else if (this.tab === 'done') {
          const page = await listDone(params)
          records = page.records || []
          total = Number(page.total) || 0
        } else {
          const page = await listInstances({ scope: 'mine', ...params })
          records = page.records || []
          total = Number(page.total) || 0
        }
        this.list = this.pageNum === 1 ? records : this.list.concat(records)
        this.total = total
        this.finished = this.list.length >= total
      } catch (e) {
        this.finished = true
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.ap {
  padding-bottom: 40rpx;
}
.ap__tabs {
  display: flex;
  background: $oa-card;
  padding: 0 24rpx;
  position: sticky;
  top: 0;
  z-index: 10;
}
.ap__tab {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  color: $oa-text-secondary;
  border-bottom: 4rpx solid transparent;
}
.ap__tab--active {
  color: $oa-primary;
  font-weight: 600;
  border-bottom-color: $oa-primary;
}
.ap__tab-count {
  margin-left: 8rpx;
  padding: 0 10rpx;
  min-width: 30rpx;
  height: 30rpx;
  line-height: 30rpx;
  text-align: center;
  border-radius: 16rpx;
  background: $oa-primary-light;
  color: $oa-primary;
  font-size: 20rpx;
}
.ap__state {
  padding: 80rpx 0;
  text-align: center;
  color: $oa-text-muted;
  font-size: 26rpx;
}
.ap__card {
  margin: 20rpx 24rpx;
  padding: 24rpx 28rpx;
  background: $oa-card;
  border-radius: $oa-radius;
}
.ap__card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.ap__card-title {
  flex: 1;
  min-width: 0;
  font-size: 29rpx;
  font-weight: 600;
  color: $oa-text;
  margin-right: 16rpx;
}
.ap__card-body {
  margin-top: 12rpx;
  display: flex;
  flex-wrap: wrap;
}
.ap__card-meta {
  font-size: 23rpx;
  color: $oa-text-muted;
  margin-right: 24rpx;
}
.ap__card-foot {
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid $oa-border;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ap__card-task {
  font-size: 24rpx;
  color: $oa-text-secondary;
}
.ap__card-action {
  font-size: 24rpx;
  color: $oa-primary;
}
.ap__more {
  padding: 24rpx 0 8rpx;
  text-align: center;
  font-size: 24rpx;
  color: $oa-text-muted;
}
.ap__create {
  margin: 20rpx 24rpx;
  height: 84rpx;
  line-height: 84rpx;
  text-align: center;
  background: $oa-primary;
  color: #ffffff;
  border-radius: 42rpx;
  font-size: 28rpx;
}
.ap__create:active {
  opacity: 0.85;
}
</style>

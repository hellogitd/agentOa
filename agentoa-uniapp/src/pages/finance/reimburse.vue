<template>
  <view class="rb">
    <view v-if="loading && list.length === 0" class="rb__state">加载中…</view>
    <oa-empty v-else-if="list.length === 0" text="暂无报销单" hint="可从工作台发起报销申请" />

    <view v-for="item in list" :key="item.id" class="rb__card" @click="openDetail(item)">
      <view class="rb__card-head">
        <text class="rb__card-title">{{ item.title || '报销申请' }}</text>
        <oa-tag :text="statusText(item.status)" :tone="statusTone(item.status)" />
      </view>
      <view class="rb__card-body">
        <text class="rb__card-meta">金额 {{ amountOf(item) }}</text>
        <text class="rb__card-meta">{{ fmtDateTime(item.createTime) }}</text>
      </view>
      <view class="rb__card-foot">
        <text class="rb__card-task">{{ flowHint(item) }}</text>
        <text class="rb__card-action">查看进度 ›</text>
      </view>
    </view>

    <view v-if="list.length" class="rb__more">
      <text v-if="hasMore" @click="loadMore">加载更多</text>
      <text v-else>没有更多了</text>
    </view>
  </view>
</template>

<script>
import { reimburseApi, bizStatus } from '@/api/workflow'
import { formatDateTime, formatMoney } from '@/utils/format'
import { go as navGo } from '@/utils/nav'
import OaEmpty from '@/components/oa-empty.vue'
import OaTag from '@/components/oa-tag.vue'

const PAGE_SIZE = 20

export default {
  components: { OaEmpty, OaTag },
  data() {
    return {
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
    fmtDateTime: formatDateTime,
    statusText(s) {
      return bizStatus(s).label
    },
    statusTone(s) {
      return bizStatus(s).tone
    },
    amountOf(item) {
      try {
        const data = item.formData ? JSON.parse(item.formData) : null
        const v = data && (data.totalAmount != null ? data.totalAmount : data.amount)
        return v == null || v === '' ? '-' : formatMoney(Number(v)) + ' 元'
      } catch (e) {
        return '-'
      }
    },
    flowHint(item) {
      if (item.status === 1) return '草稿未提交'
      return item.flowInstanceId ? '流程进行中，点击查看进度' : '已提交'
    },
    openDetail(item) {
      if (item.flowInstanceId) {
        navGo('/pages/approval/detail?id=' + item.flowInstanceId)
      } else {
        uni.showToast({ title: '草稿请在 PC 端继续编辑', icon: 'none' })
      }
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
        const page = await reimburseApi.list({ pageNum: this.pageNum, pageSize: PAGE_SIZE })
        const records = page.records || []
        this.list = this.pageNum === 1 ? records : this.list.concat(records)
        this.total = Number(page.total) || 0
        this.finished = this.list.length >= this.total
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
.rb {
  padding-bottom: 40rpx;
}
.rb__state {
  padding: 40rpx;
  text-align: center;
  color: $oa-text-muted;
  font-size: 26rpx;
}
.rb__card {
  margin: 20rpx 24rpx;
  padding: 24rpx;
  background: #ffffff;
  border-radius: 16rpx;
}
.rb__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.rb__card-title {
  flex: 1;
  font-size: 28rpx;
  font-weight: 600;
  color: $oa-text;
}
.rb__card-body {
  margin-top: 12rpx;
  display: flex;
  justify-content: space-between;
}
.rb__card-meta {
  font-size: 22rpx;
  color: $oa-text-secondary;
}
.rb__card-foot {
  margin-top: 14rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.rb__card-task {
  font-size: 22rpx;
  color: $oa-text-muted;
}
.rb__card-action {
  font-size: 22rpx;
  color: $oa-primary;
}
.rb__more {
  padding: 24rpx 0 40rpx;
  text-align: center;
  font-size: 22rpx;
  color: $oa-text-muted;
}
</style>

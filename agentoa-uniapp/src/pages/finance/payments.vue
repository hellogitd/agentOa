<template>
  <view class="pay">
    <view v-if="loading && list.length === 0" class="pay__state">加载中…</view>
    <oa-empty v-else-if="list.length === 0" text="暂无付款记录" hint="付款登记由财务/出纳维护" />

    <view v-for="item in list" :key="item.id" class="pay__card">
      <view class="pay__card-head">
        <text class="pay__card-title">{{ item.paymentNo || '付款登记' }}</text>
        <oa-tag :text="item.payStatus || '-'" :tone="item.payStatus === 'PAID' ? 'success' : 'warning'" />
      </view>
      <view class="pay__row">
        <text class="pay__row-label">报销单</text>
        <text class="pay__row-value">{{ item.reimburseNo || '-' }}（{{ item.applicantName || '-' }}）</text>
      </view>
      <view class="pay__row">
        <text class="pay__row-label">金额</text>
        <text class="pay__row-value">{{ fmtMoney(Number(item.amount)) }} 元</text>
      </view>
      <view class="pay__row">
        <text class="pay__row-label">付款日期</text>
        <text class="pay__row-value">{{ item.payDate || '-' }} · {{ methodText(item.paymentMethod) }}</text>
      </view>
      <view class="pay__row" v-if="item.voucherNo">
        <text class="pay__row-label">凭证号</text>
        <text class="pay__row-value">{{ item.voucherNo }}</text>
      </view>
    </view>

    <view v-if="list.length" class="pay__more">
      <text v-if="hasMore" @click="loadMore">加载更多</text>
      <text v-else>没有更多了</text>
    </view>
  </view>
</template>

<script>
import { listPayments } from '@/api/finance'
import { formatMoney } from '@/utils/format'
import OaEmpty from '@/components/oa-empty.vue'
import OaTag from '@/components/oa-tag.vue'

const PAGE_SIZE = 20
const PAYMENT_METHOD = { BANK_TRANSFER: '银行转账', CASH: '现金', OTHER: '其他' }

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
    fmtMoney: formatMoney,
    methodText(m) {
      return PAYMENT_METHOD[m] || m || '-'
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
        const page = await listPayments({ pageNum: this.pageNum, pageSize: PAGE_SIZE })
        const records = (page && page.records) || []
        this.list = this.pageNum === 1 ? records : this.list.concat(records)
        this.total = Number(page && page.total) || 0
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
.pay {
  padding-bottom: 40rpx;
}
.pay__state {
  padding: 40rpx;
  text-align: center;
  color: $oa-text-muted;
  font-size: 26rpx;
}
.pay__card {
  margin: 20rpx 24rpx;
  padding: 24rpx;
  background: #ffffff;
  border-radius: 16rpx;
}
.pay__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}
.pay__card-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $oa-text;
}
.pay__row {
  display: flex;
  padding: 8rpx 0;
}
.pay__row-label {
  width: 140rpx;
  font-size: 24rpx;
  color: $oa-text-secondary;
}
.pay__row-value {
  flex: 1;
  font-size: 24rpx;
  color: $oa-text;
}
.pay__more {
  padding: 24rpx 0 40rpx;
  text-align: center;
  font-size: 22rpx;
  color: $oa-text-muted;
}
</style>

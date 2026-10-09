<template>
  <view class="bal">
    <view class="bal__year">
      <picker mode="selector" :range="years" :value="yearIndex" @change="onYear">
        <view class="bal__year-box">{{ year }} 年</view>
      </picker>
    </view>

    <view v-if="loading && balances.length === 0" class="bal__state">加载中…</view>
    <oa-empty v-else-if="balances.length === 0" text="暂无额度记录" hint="额度由 HR 发放，未发放前不计额度" />

    <view v-for="b in balances" :key="b.id" class="bal__card">
      <view class="bal__card-head">
        <text class="bal__card-name">{{ b.typeName || b.leaveType }}</text>
        <text class="bal__card-avail">
          {{ b.quotaLimited === false || b.availableMinutes == null ? '不计额度' : '可用 ' + fmtMinutes(b.availableMinutes) }}
        </text>
      </view>
      <view class="bal__bars">
        <view class="bal__bar">
          <text class="bal__bar-label">总额度</text>
          <text class="bal__bar-value">{{ fmtMinutes(b.totalMinutes) }}</text>
        </view>
        <view class="bal__bar">
          <text class="bal__bar-label">冻结</text>
          <text class="bal__bar-value">{{ fmtMinutes(b.frozenMinutes) }}</text>
        </view>
        <view class="bal__bar">
          <text class="bal__bar-label">已用</text>
          <text class="bal__bar-value">{{ fmtMinutes(b.usedMinutes) }}</text>
        </view>
        <view class="bal__bar" v-if="b.expireDate">
          <text class="bal__bar-label">有效期至</text>
          <text class="bal__bar-value">{{ b.expireDate }}</text>
        </view>
      </view>
    </view>

    <view class="bal__card">
      <view class="bal__card-head">
        <text class="bal__card-name">额度流水</text>
        <text class="bal__card-link" @click="openCorrection">补卡申请</text>
        <text class="bal__card-link" @click="reload">刷新</text>
      </view>
      <view v-if="ledger.length === 0" class="bal__empty">暂无流水</view>
      <view v-for="it in ledger" :key="it.id" class="bal__ledger">
        <view class="bal__ledger-main">
          <text class="bal__ledger-action">{{ actionName(it.action) }}</text>
          <text class="bal__ledger-time">{{ fmtDateTime(it.createTime) }}</text>
        </view>
        <view class="bal__ledger-delta">
          <text v-if="it.leaveMinutes" class="bal__ledger-minutes">{{ fmtMinutes(it.leaveMinutes) }}</text>
          <text class="bal__ledger-tag">{{ it.businessType || '-' }}{{ it.submissionNo ? ' #' + it.submissionNo : '' }}</text>
        </view>
        <text v-if="it.operatorName" class="bal__ledger-op">操作人 {{ it.operatorName }}</text>
      </view>
      <view v-if="ledger.length" class="bal__more">
        <text v-if="hasMore" @click="loadMore">加载更多</text>
        <text v-else>没有更多了</text>
      </view>
    </view>
  </view>
</template>

<script>
import { myBalance, myLedger } from '@/api/attendance'
import { formatMinutes, formatDateTime } from '@/utils/format'
import { openQuickApply } from '@/utils/quick-apply'
import OaEmpty from '@/components/oa-empty.vue'

const ACTION_NAME = {
  GRANT: '发放',
  FREEZE: '冻结',
  SETTLE: '结算',
  RELEASE: '释放',
  ADJUST: '调整'
}
const PAGE_SIZE = 20

export default {
  components: { OaEmpty },
  data() {
    const y = new Date().getFullYear()
    return {
      year: y,
      years: [y - 1, y, y + 1],
      balances: [],
      ledger: [],
      pageNum: 1,
      total: 0,
      loading: false,
      finished: false
    }
  },
  computed: {
    yearIndex() {
      return this.years.indexOf(this.year)
    },
    hasMore() {
      return !this.finished && this.ledger.length < this.total
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
    fmtMinutes: formatMinutes,
    fmtDateTime: formatDateTime,
    openCorrection() {
      openQuickApply('correction')
    },
    actionName(a) {
      return ACTION_NAME[a] || a
    },
    onYear(e) {
      this.year = this.years[Number(e.detail.value)]
      this.reload()
    },
    reload() {
      this.pageNum = 1
      this.ledger = []
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
        if (this.pageNum === 1) {
          this.balances = (await myBalance(this.year)) || []
        }
        const page = await myLedger({ year: this.year, pageNum: this.pageNum, pageSize: PAGE_SIZE })
        const records = page.records || []
        this.ledger = this.pageNum === 1 ? records : this.ledger.concat(records)
        this.total = Number(page.total) || 0
        this.finished = this.ledger.length >= this.total
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
.bal {
  padding-bottom: 40rpx;
}
.bal__year {
  padding: 20rpx 24rpx;
  background: $oa-card;
}
.bal__year-box {
  height: 68rpx;
  line-height: 68rpx;
  text-align: center;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 26rpx;
  color: $oa-text;
}
.bal__state {
  padding: 80rpx 0;
  text-align: center;
  color: $oa-text-muted;
}
.bal__card {
  margin: 20rpx 24rpx;
  padding: 24rpx 28rpx;
  background: $oa-card;
  border-radius: $oa-radius;
}
.bal__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.bal__card-name {
  font-size: 28rpx;
  font-weight: 600;
  color: $oa-text;
}
.bal__card-avail {
  font-size: 26rpx;
  color: $oa-primary;
}
.bal__card-link {
  font-size: 24rpx;
  color: $oa-primary;
}
.bal__bars {
  margin-top: 18rpx;
}
.bal__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.bal__bar:last-child {
  border-bottom: none;
}
.bal__bar-label {
  font-size: 24rpx;
  color: $oa-text-muted;
}
.bal__bar-value {
  font-size: 24rpx;
  color: $oa-text;
}
.bal__empty {
  padding: 24rpx 0;
  text-align: center;
  color: $oa-text-muted;
  font-size: 24rpx;
}
.bal__ledger {
  padding: 18rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.bal__ledger:last-child {
  border-bottom: none;
}
.bal__ledger-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.bal__ledger-action {
  font-size: 26rpx;
  font-weight: 600;
  color: $oa-text;
}
.bal__ledger-time {
  font-size: 22rpx;
  color: $oa-text-muted;
}
.bal__ledger-delta {
  margin-top: 8rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.bal__ledger-minutes {
  font-size: 25rpx;
  color: $oa-primary;
}
.bal__ledger-tag {
  font-size: 22rpx;
  color: $oa-text-muted;
}
.bal__ledger-op {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: $oa-text-muted;
}
.bal__more {
  padding: 20rpx 0 4rpx;
  text-align: center;
  font-size: 24rpx;
  color: $oa-text-muted;
}
</style>

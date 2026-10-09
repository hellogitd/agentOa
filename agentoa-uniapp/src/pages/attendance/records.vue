<template>
  <view class="rec">
    <view class="rec__filter">
      <picker mode="date" :value="dateFrom" @change="onFrom">
        <view class="rec__filter-box">{{ dateFrom || '开始日期' }}</view>
      </picker>
      <text class="rec__filter-sep">至</text>
      <picker mode="date" :value="dateTo" @change="onTo">
        <view class="rec__filter-box">{{ dateTo || '结束日期' }}</view>
      </picker>
      <view class="rec__filter-btn" @click="reload">查询</view>
    </view>

    <view v-if="loading && list.length === 0" class="rec__state">加载中…</view>
    <oa-empty v-else-if="list.length === 0" text="暂无打卡记录" hint="调整日期范围后重新查询" />

    <view v-for="item in list" :key="item.id" class="rec__card">
      <view class="rec__card-head">
        <text class="rec__card-time">{{ fmtDateTime(item.punchTime) }}</text>
        <oa-tag :text="typeText(item.punchType)" :tone="item.punchType === 1 ? 'primary' : 'success'" />
      </view>
      <view class="rec__card-body">
        <view class="rec__row">
          <text class="rec__row-label">日期</text>
          <text class="rec__row-value">{{ item.punchDate }}</text>
        </view>
        <view class="rec__row">
          <text class="rec__row-label">判定</text>
          <text class="rec__row-value">{{ judgeText(item) }}</text>
        </view>
        <view class="rec__row" v-if="item.address">
          <text class="rec__row-label">位置</text>
          <text class="rec__row-value">{{ item.address }}</text>
        </view>
        <view class="rec__row" v-if="item.lng != null && item.lat != null">
          <text class="rec__row-label">坐标</text>
          <text class="rec__row-value">{{ item.lng }}, {{ item.lat }}<text v-if="item.accuracyMeters">（精度 {{ item.accuracyMeters }}m）</text></text>
        </view>
        <view class="rec__row" v-if="item.photoFileId">
          <text class="rec__row-label">照片</text>
          <image
            v-if="photos[item.id]"
            class="rec__photo"
            :src="photos[item.id]"
            mode="aspectFill"
            @click="previewPhoto(item)"
          />
          <text v-else class="rec__row-value">加载中…</text>
        </view>
        <view class="rec__row">
          <text class="rec__row-label">设备</text>
          <text class="rec__row-value">{{ item.device || '-' }} · 来源 {{ sourceText(item.source) }}</text>
        </view>
      </view>
    </view>

    <view v-if="list.length" class="rec__more">
      <text v-if="hasMore" @click="loadMore">加载更多</text>
      <text v-else>没有更多了</text>
    </view>
  </view>
</template>

<script>
import { punchRecords, PUNCH_TYPE } from '@/api/attendance'
import { previewImage } from '@/api/file'
import { formatDateTime, formatDate } from '@/utils/format'
import OaEmpty from '@/components/oa-empty.vue'
import OaTag from '@/components/oa-tag.vue'

const PAGE_SIZE = 20

function firstDayOfMonth() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
}

export default {
  components: { OaEmpty, OaTag },
  data() {
    return {
      dateFrom: firstDayOfMonth(),
      dateTo: formatDate(new Date()),
      list: [],
      photos: {},
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
    typeText(t) {
      return PUNCH_TYPE[t] || `类型 ${t}`
    },
    sourceText(s) {
      return { 1: 'PC', 2: 'App/H5', 3: '小程序' }[s] || '-'
    },
    judgeText(item) {
      const parts = []
      if (item.isLate) parts.push(`迟到 ${item.lateMinutes || 0} 分钟`)
      if (item.isEarly) parts.push(`早退 ${item.earlyMinutes || 0} 分钟`)
      return parts.length ? parts.join('，') : '正常'
    },
    onFrom(e) {
      this.dateFrom = e.detail.value
    },
    onTo(e) {
      this.dateTo = e.detail.value
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
        const page = await punchRecords({
          dateFrom: this.dateFrom,
          dateTo: this.dateTo,
          pageNum: this.pageNum,
          pageSize: PAGE_SIZE
        })
        const records = page.records || []
        this.list = this.pageNum === 1 ? records : this.list.concat(records)
        this.total = Number(page.total) || 0
        this.finished = this.list.length >= this.total
        this.loadPhotos(records)
      } catch (e) {
        this.finished = true
      } finally {
        this.loading = false
      }
    },
    loadPhotos(records) {
      records.forEach((item) => {
        if (item.photoFileId && !this.photos[item.id]) {
          previewImage(item.photoFileId)
            .then((uri) => {
              this.photos = { ...this.photos, [item.id]: uri }
            })
            .catch(() => {})
        }
      })
    },
    previewPhoto(item) {
      const uri = this.photos[item.id]
      if (uri) {
        uni.previewImage({ urls: [uri] })
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.rec {
  padding-bottom: 40rpx;
}
.rec__filter {
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;
  background: $oa-card;
}
.rec__filter-box {
  flex: 1;
  height: 68rpx;
  line-height: 68rpx;
  padding: 0 20rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 25rpx;
  color: $oa-text;
  text-align: center;
}
.rec__filter-sep {
  margin: 0 16rpx;
  color: $oa-text-muted;
  font-size: 24rpx;
}
.rec__filter-btn {
  margin-left: 16rpx;
  height: 68rpx;
  line-height: 68rpx;
  padding: 0 28rpx;
  background: $oa-primary;
  color: #ffffff;
  border-radius: 12rpx;
  font-size: 25rpx;
}
.rec__state {
  padding: 80rpx 0;
  text-align: center;
  color: $oa-text-muted;
}
.rec__card {
  margin: 20rpx 24rpx;
  padding: 24rpx 28rpx;
  background: $oa-card;
  border-radius: $oa-radius;
}
.rec__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.rec__card-time {
  font-size: 28rpx;
  font-weight: 600;
  color: $oa-text;
}
.rec__card-body {
  margin-top: 12rpx;
}
.rec__row {
  display: flex;
  align-items: flex-start;
  padding: 10rpx 0;
}
.rec__row-label {
  width: 120rpx;
  flex-shrink: 0;
  font-size: 24rpx;
  color: $oa-text-muted;
}
.rec__row-value {
  flex: 1;
  min-width: 0;
  font-size: 24rpx;
  color: $oa-text;
  text-align: right;
  word-break: break-all;
}
.rec__more {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: $oa-text-muted;
}
</style>

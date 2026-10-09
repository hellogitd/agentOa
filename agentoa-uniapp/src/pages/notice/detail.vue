<template>
  <view class="nd">
    <view v-if="loading" class="nd__state">加载中…</view>
    <oa-empty v-else-if="!detail" text="公告不存在或不在您的受众范围" />

    <block v-else>
      <view class="nd__head">
        <text class="nd__title">{{ detail.title }}</text>
        <view class="nd__meta">
          <text>{{ detail.publisherName || '发布人' }}</text>
          <text>{{ fmtDateTime(detail.publishTime) }}</text>
          <text v-if="detail.noticeType">{{ detail.noticeType }}</text>
        </view>
        <view class="nd__counts">
          <text>已读 {{ detail.readCount }}/{{ detail.audienceCount }}</text>
          <text v-if="detail.read" class="nd__read-flag">已读</text>
          <text v-else class="nd__unread-flag" @click="markRead">标记已读</text>
        </view>
      </view>

      <view class="nd__body">
        <text class="nd__content" user-select selectable>{{ detail.content }}</text>
      </view>

      <view v-if="detail.effectiveStart || detail.effectiveEnd" class="nd__range">
        <text>有效期：{{ fmtDateTime(detail.effectiveStart) || '-' }} ~ {{ fmtDateTime(detail.effectiveEnd) || '-' }}</text>
      </view>
    </block>
  </view>
</template>

<script>
import { getNotice, readNotice } from '@/api/notice'
import { formatDateTime } from '@/utils/format'
import OaEmpty from '@/components/oa-empty.vue'

export default {
  components: { OaEmpty },
  data() {
    return {
      id: '',
      loading: true,
      detail: null
    }
  },
  onLoad(options) {
    this.id = (options && options.id) || ''
    this.fetch()
  },
  methods: {
    fmtDateTime: formatDateTime,
    async fetch() {
      this.loading = true
      try {
        this.detail = await getNotice(this.id)
        if (this.detail && !this.detail.read) {
          this.markRead(true)
        }
      } catch (e) {
        this.detail = null
      } finally {
        this.loading = false
      }
    },
    async markRead(silent) {
      if (!this.detail || this.detail.read) return
      try {
        await readNotice(this.id)
        this.detail.read = true
        this.detail.readCount = Number(this.detail.readCount || 0) + 1
        if (!silent) uni.showToast({ title: '已标记为已读', icon: 'success' })
      } catch (e) {
        // 已读失败不影响阅读
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.nd {
  padding-bottom: 40rpx;
}
.nd__state {
  padding: 80rpx 0;
  text-align: center;
  color: $oa-text-muted;
}
.nd__head {
  margin: 20rpx 24rpx 0;
  padding: 28rpx;
  background: $oa-card;
  border-radius: $oa-radius;
}
.nd__title {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
  color: $oa-text;
  line-height: 1.5;
}
.nd__meta {
  margin-top: 16rpx;
  display: flex;
  flex-wrap: wrap;
}
.nd__meta text {
  font-size: 22rpx;
  color: $oa-text-muted;
  margin-right: 24rpx;
}
.nd__counts {
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid $oa-border;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.nd__counts text {
  font-size: 23rpx;
  color: $oa-text-muted;
}
.nd__read-flag {
  color: $oa-success;
}
.nd__unread-flag {
  color: $oa-primary;
}
.nd__body {
  margin: 20rpx 24rpx 0;
  padding: 28rpx;
  background: $oa-card;
  border-radius: $oa-radius;
}
.nd__content {
  display: block;
  font-size: 28rpx;
  color: $oa-text;
  line-height: 1.9;
  white-space: pre-wrap;
  word-break: break-all;
}
.nd__range {
  margin: 20rpx 24rpx 0;
  padding: 20rpx 28rpx;
  background: $oa-card;
  border-radius: $oa-radius;
}
.nd__range text {
  font-size: 23rpx;
  color: $oa-text-muted;
}
</style>

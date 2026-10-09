<template>
  <view class="nt">
    <view v-if="loading && list.length === 0" class="nt__state">加载中…</view>
    <oa-empty v-else-if="list.length === 0" text="暂无公告" hint="仅显示受众包含您且已发布的公告" />

    <view v-for="item in list" :key="item.id" class="nt__card" @click="open(item)">
      <view class="nt__card-head">
        <text class="nt__card-title">
          <text v-if="item.isTop" class="nt__card-top">置顶</text>{{ item.title }}
        </text>
        <text v-if="!item.read" class="nt__card-dot" />
      </view>
      <view class="nt__card-meta">
        <text>{{ item.publisherName || '发布人' }}</text>
        <text>{{ fmtDateTime(item.publishTime) }}</text>
      </view>
      <view class="nt__card-foot">
        <text class="nt__card-count">已读 {{ item.readCount }}/{{ item.audienceCount }}</text>
        <text class="nt__card-link">查看详情 ›</text>
      </view>
    </view>

    <view v-if="list.length" class="nt__more">
      <text v-if="hasMore" @click="loadMore">加载更多</text>
      <text v-else>没有更多了</text>
    </view>
  </view>
</template>

<script>
import { listNotice } from '@/api/notice'
import { formatDateTime } from '@/utils/format'
import { go as navGo } from '@/utils/nav'
import OaEmpty from '@/components/oa-empty.vue'

const PAGE_SIZE = 20

export default {
  components: { OaEmpty },
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
    open(item) {
      navGo(`/pages/notice/detail?id=${item.id}`)
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
        const page = await listNotice({ pageNum: this.pageNum, pageSize: PAGE_SIZE })
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
.nt {
  padding-bottom: 40rpx;
}
.nt__state {
  padding: 80rpx 0;
  text-align: center;
  color: $oa-text-muted;
}
.nt__card {
  margin: 20rpx 24rpx;
  padding: 26rpx 28rpx;
  background: $oa-card;
  border-radius: $oa-radius;
}
.nt__card-head {
  display: flex;
  align-items: flex-start;
}
.nt__card-title {
  flex: 1;
  min-width: 0;
  font-size: 29rpx;
  font-weight: 600;
  color: $oa-text;
}
.nt__card-top {
  display: inline-block;
  margin-right: 10rpx;
  padding: 2rpx 10rpx;
  background: #fdeceb;
  color: $oa-danger;
  font-size: 20rpx;
  border-radius: 6rpx;
  font-weight: 400;
}
.nt__card-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #ff4d4f;
  margin: 10rpx 0 0 12rpx;
}
.nt__card-meta {
  margin-top: 12rpx;
  display: flex;
}
.nt__card-meta text {
  font-size: 22rpx;
  color: $oa-text-muted;
  margin-right: 24rpx;
}
.nt__card-foot {
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid $oa-border;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.nt__card-count {
  font-size: 22rpx;
  color: $oa-text-muted;
}
.nt__card-link {
  font-size: 24rpx;
  color: $oa-primary;
}
.nt__more {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: $oa-text-muted;
}
</style>

<template>
  <view class="ct">
    <view class="ct__search">
      <input
        class="ct__search-input"
        v-model="keyword"
        placeholder="搜索姓名"
        confirm-type="search"
        @confirm="reload"
      />
      <view class="ct__search-btn" @click="reload">搜索</view>
    </view>

    <view v-if="loading && list.length === 0" class="ct__state">加载中…</view>
    <oa-empty v-else-if="list.length === 0" text="未找到员工" hint="换个关键词试试" />

    <view v-for="item in list" :key="item.id" class="ct__card">
      <view class="ct__card-head" @click="toggle(item)">
        <view class="ct__avatar">{{ (item.name || '?').slice(0, 1) }}</view>
        <view class="ct__card-main">
          <text class="ct__card-name">{{ item.name || '-' }}</text>
          <text class="ct__card-meta">{{ [item.deptName, item.postName].filter(Boolean).join(' · ') || '未设置部门/岗位' }}</text>
        </view>
        <text class="ct__card-arrow">{{ expanded[item.id] ? '∨' : '›' }}</text>
      </view>
      <view v-if="expanded[item.id]" class="ct__card-body">
        <view class="ct__row" v-if="item.phone">
          <text class="ct__row-label">手机</text>
          <text class="ct__row-value">{{ item.phone }}</text>
          <text class="ct__row-act" @click="callPhone(item.phone)">拨打</text>
          <text class="ct__row-act" @click="copy(item.phone)">复制</text>
        </view>
        <view class="ct__row" v-if="item.email">
          <text class="ct__row-label">邮箱</text>
          <text class="ct__row-value">{{ item.email }}</text>
          <text class="ct__row-act" @click="copy(item.email)">复制</text>
        </view>
        <view class="ct__row">
          <text class="ct__row-label">状态</text>
          <text class="ct__row-value">{{ statusText(item.status) }}</text>
        </view>
      </view>
    </view>

    <view v-if="list.length" class="ct__more">
      <text v-if="hasMore" @click="loadMore">加载更多</text>
      <text v-else>没有更多了</text>
    </view>
  </view>
</template>

<script>
import { listEmployees, employeeStatus } from '@/api/hr'
import OaEmpty from '@/components/oa-empty.vue'

const PAGE_SIZE = 20

export default {
  components: { OaEmpty },
  data() {
    return {
      keyword: '',
      list: [],
      expanded: {},
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
    statusText(s) {
      return employeeStatus(s).label
    },
    toggle(item) {
      this.expanded = { ...this.expanded, [item.id]: !this.expanded[item.id] }
    },
    callPhone(phone) {
      uni.makePhoneCall({ phoneNumber: String(phone), fail: () => {} })
    },
    copy(text) {
      uni.setClipboardData({
        data: String(text),
        success: () => uni.showToast({ title: '已复制', icon: 'none' })
      })
    },
    reload() {
      this.pageNum = 1
      this.list = []
      this.expanded = {}
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
        const page = await listEmployees({
          name: this.keyword && this.keyword.trim() ? this.keyword.trim() : undefined,
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
    }
  }
}
</script>

<style lang="scss" scoped>
.ct {
  padding-bottom: 40rpx;
}
.ct__search {
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;
}
.ct__search-input {
  flex: 1;
  height: 72rpx;
  padding: 0 20rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 26rpx;
}
.ct__search-btn {
  margin-left: 16rpx;
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 28rpx;
  background: $oa-primary;
  color: #ffffff;
  border-radius: 12rpx;
  font-size: 26rpx;
}
.ct__state {
  padding: 40rpx;
  text-align: center;
  color: $oa-text-muted;
  font-size: 26rpx;
}
.ct__card {
  margin: 16rpx 24rpx;
  padding: 20rpx 24rpx;
  background: #ffffff;
  border-radius: 16rpx;
}
.ct__card-head {
  display: flex;
  align-items: center;
}
.ct__avatar {
  width: 76rpx;
  height: 76rpx;
  line-height: 76rpx;
  text-align: center;
  background: $oa-primary-light;
  color: $oa-primary;
  border-radius: 50%;
  font-size: 30rpx;
}
.ct__card-main {
  flex: 1;
  margin-left: 20rpx;
}
.ct__card-name {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: $oa-text;
}
.ct__card-meta {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: $oa-text-secondary;
}
.ct__card-arrow {
  font-size: 28rpx;
  color: $oa-text-muted;
}
.ct__card-body {
  margin-top: 16rpx;
  border-top: 1rpx solid $oa-border;
  padding-top: 12rpx;
}
.ct__row {
  display: flex;
  align-items: center;
  padding: 10rpx 0;
}
.ct__row-label {
  width: 90rpx;
  font-size: 24rpx;
  color: $oa-text-secondary;
}
.ct__row-value {
  flex: 1;
  font-size: 24rpx;
  color: $oa-text;
}
.ct__row-act {
  margin-left: 20rpx;
  font-size: 24rpx;
  color: $oa-primary;
}
.ct__more {
  padding: 24rpx 0 40rpx;
  text-align: center;
  font-size: 22rpx;
  color: $oa-text-muted;
}
</style>

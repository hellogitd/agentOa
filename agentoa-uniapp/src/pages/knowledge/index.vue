<template>
  <view class="kb">
    <view class="kb__search">
      <input class="kb__search-input" v-model="keyword" placeholder="搜索知识空间" confirm-type="search" @confirm="reload" />
      <view class="kb__search-btn" @click="reload">搜索</view>
    </view>

    <view v-if="loading && list.length === 0" class="kb__state">加载中…</view>
    <oa-empty v-else-if="list.length === 0" text="暂无知识空间" hint="空间由管理员/成员创建" />

    <view v-for="item in list" :key="item.id" class="kb__card" @click="openSpace(item)">
      <view class="kb__card-head">
        <text class="kb__card-name">{{ item.name }}</text>
        <oa-tag :text="item.myRole || '成员'" tone="primary" />
      </view>
      <text v-if="item.description" class="kb__card-desc">{{ item.description }}</text>
      <view class="kb__card-meta">
        <text>{{ item.memberCount || 0 }} 成员</text>
        <text>{{ item.createByName || '-' }}</text>
      </view>
    </view>

    <view v-if="list.length" class="kb__more">
      <text v-if="hasMore" @click="loadMore">加载更多</text>
      <text v-else>没有更多了</text>
    </view>
  </view>
</template>

<script>
import { listSpaces } from '@/api/knowledge'
import { go as navGo } from '@/utils/nav'
import OaEmpty from '@/components/oa-empty.vue'
import OaTag from '@/components/oa-tag.vue'

const PAGE_SIZE = 20

export default {
  components: { OaEmpty, OaTag },
  data() {
    return {
      keyword: '',
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
    openSpace(item) {
      navGo('/pages/knowledge/documents?spaceId=' + item.id + '&name=' + encodeURIComponent(item.name || '文档'))
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
        const page = await listSpaces({
          keyword: this.keyword && this.keyword.trim() ? this.keyword.trim() : undefined,
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
.kb {
  padding-bottom: 40rpx;
}
.kb__search {
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;
}
.kb__search-input {
  flex: 1;
  height: 72rpx;
  padding: 0 20rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 26rpx;
}
.kb__search-btn {
  margin-left: 16rpx;
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 28rpx;
  background: $oa-primary;
  color: #ffffff;
  border-radius: 12rpx;
  font-size: 26rpx;
}
.kb__state {
  padding: 40rpx;
  text-align: center;
  color: $oa-text-muted;
  font-size: 26rpx;
}
.kb__card {
  margin: 16rpx 24rpx;
  padding: 22rpx 24rpx;
  background: #ffffff;
  border-radius: 16rpx;
}
.kb__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.kb__card-name {
  flex: 1;
  font-size: 28rpx;
  font-weight: 600;
  color: $oa-text;
}
.kb__card-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $oa-text-secondary;
}
.kb__card-meta {
  margin-top: 10rpx;
  display: flex;
  justify-content: space-between;
  font-size: 20rpx;
  color: $oa-text-muted;
}
.kb__more {
  padding: 24rpx 0 40rpx;
  text-align: center;
  font-size: 22rpx;
  color: $oa-text-muted;
}
</style>

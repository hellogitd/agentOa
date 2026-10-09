<template>
  <view class="kd">
    <view class="kd__search">
      <input class="kd__search-input" v-model="keyword" placeholder="搜索文档标题" confirm-type="search" @confirm="reload" />
      <view class="kd__search-btn" @click="reload">搜索</view>
    </view>

    <view v-if="loading && list.length === 0" class="kd__state">加载中…</view>
    <oa-empty v-else-if="list.length === 0" text="暂无文档" hint="文档在 PC 端创建与编辑" />

    <view v-for="item in list" :key="docId(item)" class="kd__card">
      <view class="kd__card-main" @click="openDoc(item)">
        <text class="kd__card-title">{{ item.title }}</text>
        <text v-if="item.highlight" class="kd__card-hit">{{ item.highlight }}</text>
        <view class="kd__card-meta">
          <text>{{ item.spaceName || spaceName || '' }}</text>
          <text>{{ fmtDate(item.lastEditTime || item.updateTime || item.createTime) }}</text>
        </view>
      </view>
      <text
        class="kd__card-star"
        :class="{ 'kd__card-star--on': isFav(docId(item)) }"
        @click="toggleFav(item)"
      >{{ isFav(docId(item)) ? '★' : '☆' }}</text>
    </view>

    <view v-if="list.length" class="kd__more">
      <text v-if="hasMore" @click="loadMore">加载更多</text>
      <text v-else>没有更多了</text>
    </view>
  </view>
</template>

<script>
import { listDocuments, searchDocuments, listFavorites, favorite, unfavorite } from '@/api/knowledge'
import { formatDate } from '@/utils/format'
import { go as navGo } from '@/utils/nav'
import OaEmpty from '@/components/oa-empty.vue'

const PAGE_SIZE = 20

export default {
  components: { OaEmpty },
  data() {
    return {
      spaceId: '',
      spaceName: '',
      keyword: '',
      list: [],
      favorites: [],
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
  onLoad(options) {
    this.spaceId = (options && options.spaceId) || ''
    this.spaceName = options && options.name ? decodeURIComponent(options.name) : ''
    if (this.spaceName) uni.setNavigationBarTitle({ title: this.spaceName })
  },
  onShow() {
    this.loadFavorites()
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
    docId(item) {
      return String(item.documentId || item.id || '')
    },
    isFav(id) {
      return this.favorites.map(String).includes(String(id))
    },
    async loadFavorites() {
      try {
        this.favorites = (await listFavorites()) || []
      } catch (e) {
        this.favorites = []
      }
    },
    async toggleFav(item) {
      const id = this.docId(item)
      if (!id) return
      try {
        if (this.isFav(id)) {
          await unfavorite(id)
          this.favorites = this.favorites.filter((x) => String(x) !== String(id))
        } else {
          await favorite(id)
          this.favorites = this.favorites.concat([id])
        }
      } catch (e) {
        // 错误信封由请求层提示
      }
    },
    openDoc(item) {
      navGo('/pages/knowledge/preview?id=' + this.docId(item))
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
        const searching = !!(this.keyword && this.keyword.trim())
        const page = searching
          ? await searchDocuments({ keyword: this.keyword.trim(), pageNum: this.pageNum, pageSize: PAGE_SIZE })
          : await listDocuments({ spaceId: this.spaceId || undefined, pageNum: this.pageNum, pageSize: PAGE_SIZE })
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
.kd {
  padding-bottom: 40rpx;
}
.kd__search {
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;
}
.kd__search-input {
  flex: 1;
  height: 72rpx;
  padding: 0 20rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 26rpx;
}
.kd__search-btn {
  margin-left: 16rpx;
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 28rpx;
  background: $oa-primary;
  color: #ffffff;
  border-radius: 12rpx;
  font-size: 26rpx;
}
.kd__state {
  padding: 40rpx;
  text-align: center;
  color: $oa-text-muted;
  font-size: 26rpx;
}
.kd__card {
  margin: 16rpx 24rpx;
  padding: 22rpx 24rpx;
  background: #ffffff;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
}
.kd__card-main {
  flex: 1;
}
.kd__card-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: $oa-text;
}
.kd__card-hit {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: $oa-text-secondary;
}
.kd__card-meta {
  margin-top: 8rpx;
  display: flex;
  justify-content: space-between;
  font-size: 20rpx;
  color: $oa-text-muted;
}
.kd__card-star {
  margin-left: 16rpx;
  font-size: 40rpx;
  color: $oa-text-muted;
}
.kd__card-star--on {
  color: #f5a623;
}
.kd__more {
  padding: 24rpx 0 40rpx;
  text-align: center;
  font-size: 22rpx;
  color: $oa-text-muted;
}
</style>

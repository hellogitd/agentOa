<template>
  <view class="msg">
    <view class="msg__bar">
      <view class="msg__tabs">
        <view
          v-for="t in tabs"
          :key="t.key"
          class="msg__tab"
          :class="{ 'msg__tab--active': type === t.key }"
          @click="switchType(t.key)"
        >
          {{ t.label }}
        </view>
      </view>
      <text class="msg__read-all" @click="readAll">全部已读</text>
    </view>

    <view v-if="loading && list.length === 0" class="msg__state">加载中…</view>
    <oa-empty v-else-if="list.length === 0" text="暂无消息" hint="实时推送仅作提醒，数据以接口为准" />

    <view
      v-for="item in list"
      :key="item.id"
      class="msg__card"
      :class="{ 'msg__card--unread': !item.isRead }"
      @click="open(item)"
    >
      <view class="msg__card-main">
        <view class="msg__card-head">
          <text class="msg__card-type">{{ typeName(item.type) }}</text>
          <text v-if="!item.isRead" class="msg__card-dot" />
          <text class="msg__card-time">{{ fmtDateTime(item.createTime) }}</text>
        </view>
        <text class="msg__card-title">{{ item.title }}</text>
        <text v-if="item.content" class="msg__card-content">{{ item.content }}</text>
      </view>
      <text class="msg__card-del" @click.stop="remove(item)">删除</text>
    </view>

    <view v-if="list.length" class="msg__more">
      <text v-if="hasMore" @click="loadMore">加载更多</text>
      <text v-else>没有更多了</text>
    </view>
  </view>
</template>

<script>
import { listMessage, readMessage, readAllMessage, removeMessage, MSG_TYPE_NAME } from '@/api/notice'
import { formatDateTime } from '@/utils/format'
import { go as navGo } from '@/utils/nav'
import { connectRealtime, onRealtimeMessage } from '@/utils/realtime'
import OaEmpty from '@/components/oa-empty.vue'

const PAGE_SIZE = 20
let unsubscribeRealtime = null

export default {
  components: { OaEmpty },
  data() {
    return {
      tabs: [
        { key: '', label: '全部' },
        { key: 'TODO', label: '待办' },
        { key: 'NOTICE', label: '公告' },
        { key: 'MENTION', label: '提及' },
        { key: 'SYSTEM', label: '系统' }
      ],
      type: '',
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
    connectRealtime()
    if (!unsubscribeRealtime) {
      unsubscribeRealtime = onRealtimeMessage(() => {
        this.reload()
      })
    }
  },
  onUnload() {
    if (unsubscribeRealtime) {
      unsubscribeRealtime()
      unsubscribeRealtime = null
    }
  },
  onPullDownRefresh() {
    this.reload().finally(() => uni.stopPullDownRefresh())
  },
  onReachBottom() {
    if (this.hasMore) this.loadMore()
  },
  methods: {
    fmtDateTime: formatDateTime,
    typeName(t) {
      return MSG_TYPE_NAME[t] || t || '消息'
    },
    switchType(key) {
      if (this.type === key) return
      this.type = key
      this.reload()
    },
    async open(item) {
      if (!item.isRead) {
        try {
          await readMessage(item.id)
          item.isRead = 1
        } catch (e) {
          // 已读失败不阻塞跳转
        }
      }
      const bizType = String(item.bizType || '').toUpperCase()
      if (bizType.indexOf('NOTICE') >= 0 && item.bizId) {
        navGo(`/pages/notice/detail?id=${item.bizId}`)
        return
      }
      if (bizType.indexOf('TASK') >= 0) {
        navGo('/pages/tasks/index')
        return
      }
      if (item.bizId && bizType !== 'SYSTEM') {
        navGo(`/pages/approval/detail?id=${item.bizId}`)
      }
    },
    remove(item) {
      uni.showModal({
        title: '删除消息',
        content: '删除后仅影响本人副本，审计记录保留。确定删除？',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await removeMessage(item.id)
            this.list = this.list.filter((it) => it.id !== item.id)
          } catch (e) {
            // 统一错误提示
          }
        }
      })
    },
    readAll() {
      uni.showModal({
        title: '全部已读',
        content: '确定把当前账号的全部消息标记为已读？',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await readAllMessage()
            this.list.forEach((it) => {
              it.isRead = 1
            })
            uni.showToast({ title: '已全部标记为已读', icon: 'success' })
          } catch (e) {
            // 统一错误提示
          }
        }
      })
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
        const params = { pageNum: this.pageNum, pageSize: PAGE_SIZE }
        if (this.type) params.type = this.type
        const page = await listMessage(params)
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
.msg {
  padding-bottom: 40rpx;
}
.msg__bar {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  background: $oa-card;
  position: sticky;
  top: 0;
  z-index: 10;
}
.msg__tabs {
  flex: 1;
  display: flex;
}
.msg__tab {
  padding: 10rpx 22rpx;
  margin-right: 12rpx;
  border-radius: 28rpx;
  background: #f0f2f7;
  color: $oa-text-secondary;
  font-size: 24rpx;
}
.msg__tab--active {
  background: $oa-primary-light;
  color: $oa-primary;
  font-weight: 600;
}
.msg__read-all {
  font-size: 24rpx;
  color: $oa-primary;
}
.msg__state {
  padding: 80rpx 0;
  text-align: center;
  color: $oa-text-muted;
}
.msg__card {
  margin: 20rpx 24rpx;
  padding: 24rpx 28rpx;
  background: $oa-card;
  border-radius: $oa-radius;
  display: flex;
  align-items: flex-start;
}
.msg__card--unread {
  border-left: 6rpx solid $oa-primary;
}
.msg__card-main {
  flex: 1;
  min-width: 0;
}
.msg__card-head {
  display: flex;
  align-items: center;
}
.msg__card-type {
  font-size: 21rpx;
  color: $oa-primary;
  background: $oa-primary-light;
  padding: 2rpx 12rpx;
  border-radius: 6rpx;
}
.msg__card-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #ff4d4f;
  margin-left: 12rpx;
}
.msg__card-time {
  margin-left: auto;
  font-size: 21rpx;
  color: $oa-text-muted;
}
.msg__card-title {
  display: block;
  margin-top: 12rpx;
  font-size: 27rpx;
  color: $oa-text;
}
.msg__card-content {
  display: block;
  margin-top: 8rpx;
  font-size: 23rpx;
  color: $oa-text-secondary;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.msg__card-del {
  margin-left: 16rpx;
  font-size: 22rpx;
  color: $oa-text-muted;
}
.msg__more {
  padding: 24rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: $oa-text-muted;
}
</style>

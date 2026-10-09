<template>
  <view class="cal">
    <view class="cal__filter">
      <picker mode="date" :value="dateFrom" @change="onFrom">
        <view class="cal__filter-box">{{ dateFrom }}</view>
      </picker>
      <text class="cal__filter-sep">至</text>
      <picker mode="date" :value="dateTo" @change="onTo">
        <view class="cal__filter-box">{{ dateTo }}</view>
      </picker>
      <view class="cal__filter-btn" @click="reload">查询</view>
    </view>

    <view v-if="canAdd" class="cal__create" @click="showCreate = true">＋ 新建日程</view>

    <view v-if="loading && list.length === 0" class="cal__state">加载中…</view>
    <oa-empty v-else-if="list.length === 0" text="暂无日程" hint="调整时间范围或新建日程" />

    <view v-for="item in list" :key="item.id" class="cal__card">
      <view class="cal__card-head">
        <text class="cal__card-title">{{ item.title }}</text>
        <oa-tag v-if="item.myResponse === 'PENDING'" text="待响应" tone="warning" />
        <oa-tag v-else-if="item.myResponse === 'REJECTED'" text="已拒绝" tone="danger" />
      </view>
      <view class="cal__row">
        <text class="cal__row-label">时间</text>
        <text class="cal__row-value">{{ fmtDateTime(item.startTime) }} ~ {{ fmtDateTime(item.endTime) }}</text>
      </view>
      <view class="cal__row" v-if="item.location">
        <text class="cal__row-label">地点</text>
        <text class="cal__row-value">{{ item.location }}</text>
      </view>
      <view class="cal__row">
        <text class="cal__row-label">组织者</text>
        <text class="cal__row-value">{{ item.organizerName || '-' }}</text>
      </view>
      <view v-if="item.myResponse === 'PENDING'" class="cal__acts">
        <view class="cal__act cal__act--ok" @click="respond(item, true)">接受</view>
        <view class="cal__act" @click="respond(item, false)">拒绝</view>
      </view>
    </view>

    <view v-if="list.length" class="cal__more">
      <text v-if="hasMore" @click="loadMore">加载更多</text>
      <text v-else>没有更多了</text>
    </view>

    <view v-if="showCreate" class="cal__mask" @click="showCreate = false">
      <view class="cal__panel" @click.stop>
        <text class="cal__panel-title">新建日程</text>
        <text-field label="标题" :value="draft.title" placeholder="请输入标题" @change="(v) => (draft.title = v)" />
        <datetime-picker label="开始时间" :value="draft.startTime" @change="(v) => (draft.startTime = v)" />
        <datetime-picker label="结束时间" :value="draft.endTime" @change="(v) => (draft.endTime = v)" />
        <text-field label="地点" :value="draft.location" placeholder="可选" @change="(v) => (draft.location = v)" />
        <view class="cal__panel-btns">
          <button class="cal__panel-btn cal__panel-btn--ghost" @click="showCreate = false">取消</button>
          <button class="cal__panel-btn cal__panel-btn--primary" :disabled="submitting" @click="submitCreate">
            {{ submitting ? '保存中…' : '保存' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { listEvents, createEvent, acceptEvent, rejectEvent } from '@/api/calendar'
import { formatDateTime, today, formatDate } from '@/utils/format'
import { hasPermission } from '@/utils/auth'
import OaEmpty from '@/components/oa-empty.vue'
import OaTag from '@/components/oa-tag.vue'
import TextField from '@/components/text-field.vue'
import DatetimePicker from '@/components/datetime-picker.vue'

const PAGE_SIZE = 20

function plusDays(n) {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return formatDate(d)
}

export default {
  components: { OaEmpty, OaTag, TextField, DatetimePicker },
  data() {
    return {
      dateFrom: today(),
      dateTo: plusDays(14),
      list: [],
      pageNum: 1,
      total: 0,
      loading: false,
      finished: false,
      showCreate: false,
      submitting: false,
      draft: { title: '', startTime: '', endTime: '', location: '' }
    }
  },
  computed: {
    hasMore() {
      return !this.finished && this.list.length < this.total
    },
    canAdd() {
      return hasPermission('cl:event:add')
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
        const page = await listEvents({
          scope: 'self',
          start: this.dateFrom,
          end: this.dateTo,
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
    },
    async respond(item, accept) {
      try {
        if (accept) {
          await acceptEvent(item.id)
        } else {
          await rejectEvent(item.id)
        }
        uni.showToast({ title: accept ? '已接受' : '已拒绝', icon: 'none' })
        this.reload()
      } catch (e) {
        // 错误信封由请求层提示
      }
    },
    async submitCreate() {
      if (this.submitting) return
      if (!this.draft.title || !this.draft.startTime || !this.draft.endTime) {
        uni.showToast({ title: '请填写标题与起止时间', icon: 'none' })
        return
      }
      this.submitting = true
      try {
        await createEvent({
          title: this.draft.title,
          startTime: this.draft.startTime,
          endTime: this.draft.endTime,
          location: this.draft.location || undefined,
          eventType: '1',
          visibility: '2'
        })
        uni.showToast({ title: '日程已创建', icon: 'success' })
        this.showCreate = false
        this.draft = { title: '', startTime: '', endTime: '', location: '' }
        this.reload()
      } catch (e) {
        // 错误信封由请求层提示
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.cal {
  padding-bottom: 40rpx;
}
.cal__filter {
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;
}
.cal__filter-box {
  padding: 12rpx 20rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 24rpx;
  color: $oa-text;
}
.cal__filter-sep {
  margin: 0 12rpx;
  color: $oa-text-muted;
}
.cal__filter-btn {
  margin-left: auto;
  padding: 12rpx 28rpx;
  background: $oa-primary;
  color: #ffffff;
  border-radius: 12rpx;
  font-size: 24rpx;
}
.cal__create {
  margin: 0 24rpx 8rpx;
  padding: 18rpx 0;
  text-align: center;
  background: $oa-primary-light;
  color: $oa-primary;
  border-radius: 12rpx;
  font-size: 26rpx;
}
.cal__state {
  padding: 40rpx;
  text-align: center;
  color: $oa-text-muted;
  font-size: 26rpx;
}
.cal__card {
  margin: 16rpx 24rpx;
  padding: 22rpx 24rpx;
  background: #ffffff;
  border-radius: 16rpx;
}
.cal__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.cal__card-title {
  flex: 1;
  font-size: 28rpx;
  font-weight: 600;
  color: $oa-text;
}
.cal__row {
  display: flex;
  padding: 8rpx 0;
}
.cal__row-label {
  width: 100rpx;
  font-size: 24rpx;
  color: $oa-text-secondary;
}
.cal__row-value {
  flex: 1;
  font-size: 24rpx;
  color: $oa-text;
}
.cal__acts {
  margin-top: 12rpx;
  display: flex;
  justify-content: flex-end;
}
.cal__act {
  margin-left: 16rpx;
  padding: 10rpx 32rpx;
  border-radius: 28rpx;
  font-size: 24rpx;
  color: $oa-text-secondary;
  background: #f0f2f7;
}
.cal__act--ok {
  color: #ffffff;
  background: $oa-primary;
}
.cal__more {
  padding: 24rpx 0 40rpx;
  text-align: center;
  font-size: 22rpx;
  color: $oa-text-muted;
}
.cal__mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 20;
}
.cal__panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  background: #ffffff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx 24rpx calc(24rpx + env(safe-area-inset-bottom));
}
.cal__panel-title {
  display: block;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
  color: $oa-text;
  margin-bottom: 12rpx;
}
.cal__panel-btns {
  margin-top: 24rpx;
  display: flex;
}
.cal__panel-btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  border-radius: 40rpx;
  font-size: 28rpx;
}
.cal__panel-btn--ghost {
  background: #f0f2f7;
  color: $oa-text-secondary;
  margin-right: 16rpx;
}
.cal__panel-btn--primary {
  background: $oa-primary;
  color: #ffffff;
}
.cal__panel-btn::after {
  border: none;
}
</style>

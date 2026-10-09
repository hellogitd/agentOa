<template>
  <view class="wb">
    <view class="wb__hero">
      <view class="wb__hero-row">
        <view>
          <text class="wb__hello">{{ greeting }}，{{ displayName }}</text>
          <text class="wb__date">{{ todayText }}</text>
        </view>
        <view class="wb__badge" @click="go('/pages/message/index')">
          <text class="wb__badge-dot" v-if="unreadTotal > 0" />
          <text class="wb__badge-text">{{ unreadTotal > 99 ? '99+' : unreadTotal }}</text>
        </view>
      </view>

      <view class="wb__punch">
        <view class="wb__punch-left">
          <text class="wb__punch-label">今日打卡</text>
          <text class="wb__punch-value">{{ punchText }}</text>
          <text class="wb__punch-sub">{{ punchSub }}</text>
        </view>
        <button class="wb__punch-btn" @click="go('/pages/attendance/index')">去打卡</button>
      </view>
    </view>

    <view class="wb__grid">
      <view v-for="item in visibleEntries" :key="item.url" class="wb__grid-item" @click="go(item.url)">
        <view class="wb__grid-icon" :style="{ background: item.color }">{{ item.icon }}</view>
        <text class="wb__grid-label">{{ item.label }}</text>
      </view>
    </view>

    <view class="wb__card" v-if="management">
      <text class="wb__card-title">管理视角</text>
      <view class="wb__mgmt">
        <view class="wb__mgmt-item">
          <text class="wb__mgmt-value">{{ management.deptAttendanceRate || '-' }}</text>
          <text class="wb__mgmt-label">本月部门出勤率</text>
        </view>
        <view class="wb__mgmt-item">
          <text class="wb__mgmt-value">{{ management.monthReimburseAmount || '-' }}</text>
          <text class="wb__mgmt-label">本月报销额（元）</text>
        </view>
        <view class="wb__mgmt-item">
          <text class="wb__mgmt-value">{{ management.avgFlowDurationHours || '-' }}</text>
          <text class="wb__mgmt-label">平均处理时长（小时）</text>
        </view>
      </view>
    </view>

    <view class="wb__card">
      <view class="wb__card-head">
        <text class="wb__card-title">我的待办</text>
        <text class="wb__card-more" @click="go('/pages/approval/index?tab=todo')">全部 {{ todoCount }}</text>
      </view>
      <view v-if="todoRecent.length === 0" class="wb__empty">
        <text>暂无待办</text>
      </view>
      <view v-for="item in todoRecent" :key="item.id" class="wb__item" @click="goApproval(item)">
        <view class="wb__item-main">
          <text class="wb__item-title">{{ item.title }}</text>
          <text class="wb__item-sub">{{ bizName(item.businessType) }} · {{ item.time }}</text>
        </view>
        <text class="wb__item-arrow">›</text>
      </view>
    </view>

    <view class="wb__card">
      <view class="wb__card-head">
        <text class="wb__card-title">我的假期余额</text>
        <text class="wb__card-more" @click="go('/pages/attendance/balance')">明细</text>
      </view>
      <view v-if="balances.length === 0" class="wb__empty">
        <text>暂无额度记录</text>
      </view>
      <view v-for="b in balances" :key="b.leaveType" class="wb__balance">
        <text class="wb__balance-name">{{ leaveName(b.leaveType) }}</text>
        <text class="wb__balance-value">{{ b.availableMinutes == null ? '不计额度' : fmtMinutes(b.availableMinutes) }}</text>
      </view>
    </view>

    <view class="wb__card">
      <view class="wb__card-head">
        <text class="wb__card-title">最新公告</text>
        <text class="wb__card-more" @click="go('/pages/notice/index')">全部</text>
      </view>
      <view v-if="notices.length === 0" class="wb__empty">
        <text>暂无公告</text>
      </view>
      <view v-for="n in notices" :key="n.id" class="wb__item" @click="go(`/pages/notice/detail?id=${n.id}`)">
        <view class="wb__item-main">
          <text class="wb__item-title">{{ n.title }}</text>
          <text class="wb__item-sub">{{ n.publishTime }}</text>
        </view>
        <text class="wb__item-arrow">›</text>
      </view>
    </view>

    <view class="wb__card" v-if="todayEvents.length">
      <view class="wb__card-head">
        <text class="wb__card-title">今日日程</text>
      </view>
      <view v-for="e in todayEvents" :key="e.id" class="wb__item">
        <view class="wb__item-main">
          <text class="wb__item-title">{{ e.title }}</text>
          <text class="wb__item-sub">{{ e.startTime }} ~ {{ e.endTime }}</text>
        </view>
      </view>
    </view>

    <view class="wb__footer">AgentOA 移动端 v1.0 · 数据以服务端接口为准</view>
  </view>
</template>

<script>
import { workbench } from '@/api/workbench'
import { unreadCount } from '@/api/notice'
import { BUSINESS_TYPE_NAME } from '@/api/workflow'
import { LEAVE_TYPES } from '@/api/attendance'
import { formatMinutes, formatDateTime, formatDate, weekday } from '@/utils/format'
import { getUser, hasPermission } from '@/utils/auth'
import { go as navGo } from '@/utils/nav'
import { connectRealtime, onRealtimeMessage, getRealtimeState } from '@/utils/realtime'

const LEAVE_NAME = {}
LEAVE_TYPES.forEach((it) => {
  LEAVE_NAME[it.value] = it.label
})
let unsubscribeRealtime = null

export default {
  data() {
    return {
      loading: false,
      todoCount: 0,
      initiatedCount: 0,
      todoRecent: [],
      initiatedRecent: [],
      todayEvents: [],
      notices: [],
      attendance: {},
      balances: [],
      management: null,
      unreadTotal: 0,
      quickEntries: [
        { label: '发起申请', icon: '审', color: '#2f6df6', url: '/pages/approval/apply' },
        { label: '我的待办', icon: '办', color: '#2fb87c', url: '/pages/approval/index?tab=todo' },
        { label: '今日打卡', icon: '卡', color: '#f0ad4e', url: '/pages/attendance/index' },
        { label: '假期余额', icon: '假', color: '#7c5cf0', url: '/pages/attendance/balance' },
        { label: '打卡记录', icon: '录', color: '#2f9df6', url: '/pages/attendance/records' },
        { label: '公告', icon: '告', color: '#e5534b', url: '/pages/notice/index' },
        { label: '消息中心', icon: '信', color: '#00a6a6', url: '/pages/message/index' },
        { label: '我的档案', icon: '档', color: '#6b7280', url: '/pages/mine/profile' },
        { label: 'AI 助手', icon: 'AI', color: '#5b6ef5', url: '/pages/ai/chat', perm: 'ai:chat:use' },
        { label: '知识问答', icon: '问', color: '#8a5cf0', url: '/pages/ai/qa', perm: 'ai:qa:use' },
        { label: '通讯录', icon: '联', color: '#0ea5e9', url: '/pages/contacts/index' },
        { label: '日程', icon: '程', color: '#14b8a6', url: '/pages/calendar/index' },
        { label: '知识库', icon: '知', color: '#f59e0b', url: '/pages/knowledge/index' },
        { label: '我的任务', icon: '任', color: '#22c55e', url: '/pages/tasks/index' },
        { label: '报销进度', icon: '报', color: '#ef4444', url: '/pages/finance/reimburse' }
      ]
    }
  },
  computed: {
    displayName() {
      const user = getUser()
      return (user && (user.nickname || user.username)) || '同事'
    },
    greeting() {
      const h = new Date().getHours()
      if (h < 6) return '夜深了'
      if (h < 12) return '早上好'
      if (h < 14) return '中午好'
      if (h < 18) return '下午好'
      return '晚上好'
    },
    todayText() {
      const d = new Date()
      return `${formatDate(d)} ${weekday(d)}`
    },
    punchText() {
      const a = this.attendance || {}
      if (a.punchInTime || a.punchOutTime) {
        return `${a.punchInTime ? '上班 ' + formatDateTime(a.punchInTime).slice(11) : '未上班打卡'}${
          a.punchOutTime ? '　下班 ' + formatDateTime(a.punchOutTime).slice(11) : ''
        }`
      }
      return '尚未打卡'
    },
    punchSub() {
      const a = this.attendance || {}
      return a.todayPunched ? '今日已产生打卡记录' : '请在考勤页完成上/下班打卡'
    },
    visibleEntries() {
      return this.quickEntries.filter((e) => !e.perm || hasPermission(e.perm))
    },
    realtimeStatus() {
      return getRealtimeState().status
    }
  },
  onShow() {
    this.refresh()
    connectRealtime()
    if (!unsubscribeRealtime) {
      unsubscribeRealtime = onRealtimeMessage(() => {
        // 推送只作触发器，未读角标以拉取为准（双轨去重）
        this.refreshUnread()
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
    this.refresh(true).finally(() => uni.stopPullDownRefresh())
  },
  methods: {
    bizName(type) {
      return BUSINESS_TYPE_NAME[type] || type
    },
    leaveName(type) {
      return LEAVE_NAME[type] || type
    },
    fmtMinutes(m) {
      return formatMinutes(m)
    },
    go(url) {
      navGo(url)
    },
    goApproval(item) {
      navGo(`/pages/approval/detail?id=${item.id}`)
    },
    async refresh(_force) {
      if (this.loading) return
      this.loading = true
      try {
        const data = await workbench()
        this.todoCount = Number(data.todoCount) || 0
        this.initiatedCount = Number(data.initiatedCount) || 0
        this.todoRecent = (data.todoRecent || []).slice(0, 5)
        this.initiatedRecent = (data.initiatedRecent || []).slice(0, 5)
        this.todayEvents = data.todayEvents || []
        this.notices = (data.notices || []).slice(0, 5)
        this.attendance = data.attendance || {}
        this.balances = (data.leaveBalances || []).slice(0, 4)
        this.management = data.management || null
      } catch (e) {
        // 工作台加载失败不阻塞页面
      } finally {
        this.loading = false
      }
      try {
        const unread = await unreadCount()
        this.unreadTotal = Number(unread && unread.total) || 0
      } catch (e) {
        this.unreadTotal = 0
      }
    },
    async refreshUnread() {
      try {
        const unread = await unreadCount()
        this.unreadTotal = Number(unread && unread.total) || 0
      } catch (e) {
        // 拉取失败保留上次角标
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.wb {
  padding-bottom: 40rpx;
}
.wb__hero {
  background: linear-gradient(160deg, $oa-primary 0%, #4b83f8 100%);
  padding: 32rpx 32rpx 40rpx;
  color: #ffffff;
}
.wb__hero-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.wb__hello {
  display: block;
  font-size: 38rpx;
  font-weight: 600;
}
.wb__date {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.82);
}
.wb__badge {
  position: relative;
  min-width: 72rpx;
  height: 56rpx;
  padding: 0 18rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
}
.wb__badge-text {
  font-size: 24rpx;
  color: #ffffff;
}
.wb__badge-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #ff6b6b;
  margin-right: 8rpx;
}
.wb__punch {
  margin-top: 32rpx;
  background: rgba(255, 255, 255, 0.16);
  border-radius: 20rpx;
  padding: 28rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.wb__punch-label {
  display: block;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.8);
}
.wb__punch-value {
  display: block;
  margin-top: 8rpx;
  font-size: 30rpx;
  font-weight: 600;
}
.wb__punch-sub {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.78);
}
.wb__punch-btn {
  margin: 0;
  height: 68rpx;
  line-height: 68rpx;
  padding: 0 32rpx;
  background: #ffffff;
  color: $oa-primary;
  font-size: 26rpx;
  border-radius: 34rpx;
  border: none;
}
.wb__punch-btn::after {
  border: none;
}
.wb__grid {
  margin: 24rpx;
  padding: 28rpx 8rpx 16rpx;
  background: $oa-card;
  border-radius: $oa-radius;
  display: flex;
  flex-wrap: wrap;
}
.wb__grid-item {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24rpx;
}
.wb__grid-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 24rpx;
  color: #ffffff;
  font-size: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.wb__grid-label {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: $oa-text-secondary;
}
.wb__card {
  margin: 20rpx 24rpx;
  padding: 24rpx 28rpx;
  background: $oa-card;
  border-radius: $oa-radius;
}
.wb__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.wb__card-title {
  font-size: 28rpx;
  font-weight: 600;
  color: $oa-text;
}
.wb__card-more {
  font-size: 24rpx;
  color: $oa-primary;
}
.wb__empty {
  padding: 32rpx 0 12rpx;
  text-align: center;
  color: $oa-text-muted;
  font-size: 24rpx;
}
.wb__item {
  display: flex;
  align-items: center;
  padding: 22rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.wb__item:last-child {
  border-bottom: none;
}
.wb__item-main {
  flex: 1;
  min-width: 0;
}
.wb__item-title {
  display: block;
  font-size: 27rpx;
  color: $oa-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wb__item-sub {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: $oa-text-muted;
}
.wb__item-arrow {
  color: $oa-text-muted;
  font-size: 32rpx;
  margin-left: 12rpx;
}
.wb__balance {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.wb__balance:last-child {
  border-bottom: none;
}
.wb__balance-name {
  font-size: 26rpx;
  color: $oa-text;
}
.wb__balance-value {
  font-size: 26rpx;
  color: $oa-primary;
}
.wb__mgmt {
  display: flex;
  margin-top: 20rpx;
}
.wb__mgmt-item {
  flex: 1;
  text-align: center;
}
.wb__mgmt-value {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: $oa-text;
}
.wb__mgmt-label {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: $oa-text-muted;
}
.wb__footer {
  padding: 24rpx 0 8rpx;
  text-align: center;
  font-size: 22rpx;
  color: $oa-text-muted;
}
</style>

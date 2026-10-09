<template>
  <view class="mine">
    <view class="mine__hero">
      <view class="mine__avatar">{{ avatarText }}</view>
      <view class="mine__info">
        <text class="mine__name">{{ user.nickname || user.username || '未登录' }}</text>
        <text class="mine__sub">{{ user.deptName || user.username || 'AgentOA' }}</text>
        <view class="mine__roles">
          <text v-for="r in roleLabels" :key="r" class="mine__role">{{ r }}</text>
        </view>
      </view>
    </view>

    <view class="mine__card">
      <view class="mine__link" @click="go('/pages/mine/profile')">
        <text class="mine__link-name">我的档案</text>
        <text class="mine__link-arrow">›</text>
      </view>
      <view class="mine__link" @click="openStarted">
        <text class="mine__link-name">我发起的流程</text>
        <text class="mine__link-arrow">›</text>
      </view>
      <view class="mine__link" @click="go('/pages/attendance/balance')">
        <text class="mine__link-name">假期余额</text>
        <text class="mine__link-arrow">›</text>
      </view>
      <view class="mine__link" @click="go('/pages/attendance/records')">
        <text class="mine__link-name">打卡记录</text>
        <text class="mine__link-arrow">›</text>
      </view>
      <view class="mine__link" @click="go('/pages/mine/password')">
        <text class="mine__link-name">修改密码</text>
        <text class="mine__link-arrow">›</text>
      </view>
    </view>

    <view class="mine__card">
      <view class="mine__link" @click="go('/pages/notice/index')">
        <text class="mine__link-name">公告</text>
        <text class="mine__link-arrow">›</text>
      </view>
      <view class="mine__link" @click="go('/pages/message/index')">
        <text class="mine__link-name">消息中心</text>
        <text class="mine__link-arrow">›</text>
      </view>
      <view class="mine__link" @click="go('/pages/contacts/index')">
        <text class="mine__link-name">通讯录</text>
        <text class="mine__link-arrow">›</text>
      </view>
      <view class="mine__link" @click="go('/pages/calendar/index')">
        <text class="mine__link-name">日程</text>
        <text class="mine__link-arrow">›</text>
      </view>
      <view class="mine__link" @click="go('/pages/knowledge/index')">
        <text class="mine__link-name">知识库</text>
        <text class="mine__link-arrow">›</text>
      </view>
      <view class="mine__link" @click="go('/pages/tasks/index')">
        <text class="mine__link-name">我的任务</text>
        <text class="mine__link-arrow">›</text>
      </view>
      <view class="mine__link" @click="go('/pages/finance/reimburse')">
        <text class="mine__link-name">报销进度</text>
        <text class="mine__link-arrow">›</text>
      </view>
      <view v-if="canPayment" class="mine__link" @click="go('/pages/finance/payments')">
        <text class="mine__link-name">付款登记</text>
        <text class="mine__link-arrow">›</text>
      </view>
    </view>

    <view v-if="canChat || canQa" class="mine__card">
      <view v-if="canChat" class="mine__link" @click="go('/pages/ai/chat')">
        <text class="mine__link-name">AI 助手</text>
        <text class="mine__link-arrow">›</text>
      </view>
      <view v-if="canQa" class="mine__link" @click="go('/pages/ai/qa')">
        <text class="mine__link-name">知识问答</text>
        <text class="mine__link-arrow">›</text>
      </view>
    </view>

    <view class="mine__logout" @click="handleLogout">退出登录</view>
    <text class="mine__version">AgentOA 移动端 v1.0</text>
  </view>
</template>

<script>
import { logout } from '@/api/auth'
import { useUserStore } from '@/store/user'
import { go as navGo, openApprovalTab } from '@/utils/nav'
import { safeReLaunch, hasPermission } from '@/utils/auth'

const ROLE_NAME = {
  hr: '人力资源',
  dept_manager: '部门经理',
  employee: '普通员工',
  finance: '财务',
  cashier: '出纳',
  director: '总监',
  admin: '管理员'
}

export default {
  data() {
    return {
      user: {}
    }
  },
  computed: {
    avatarText() {
      const n = this.user.nickname || this.user.username || 'A'
      return n.slice(0, 1).toUpperCase()
    },
    roleLabels() {
      return (this.user.roles || []).map((r) => ROLE_NAME[r] || r)
    },
    canChat() {
      return hasPermission('ai:chat:use', this.user.permissions)
    },
    canQa() {
      return hasPermission('ai:qa:use', this.user.permissions)
    },
    canPayment() {
      return hasPermission('fn:payment:list', this.user.permissions)
    }
  },
  onShow() {
    this.refresh()
  },
  onPullDownRefresh() {
    this.refresh().finally(() => uni.stopPullDownRefresh())
  },
  methods: {
    async refresh() {
      try {
        const store = useUserStore()
        const user = await store.loadProfile(true)
        this.user = user || {}
      } catch (e) {
        this.user = useUserStore().state.user || {}
      }
    },
    go(url) {
      navGo(url)
    },
    openStarted() {
      openApprovalTab('started')
    },
    handleLogout() {
      uni.showModal({
        title: '退出登录',
        content: '确定退出当前账号？',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await logout()
          } catch (e) {
            // 登出失败也清理本地会话
          }
          useUserStore().reset()
          safeReLaunch('/pages/login/login')
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.mine {
  padding-bottom: 60rpx;
}
.mine__hero {
  margin: 20rpx 24rpx;
  padding: 36rpx 32rpx;
  background: linear-gradient(150deg, $oa-primary 0%, #4b83f8 100%);
  border-radius: 24rpx;
  color: #ffffff;
  display: flex;
  align-items: center;
}
.mine__avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
  font-weight: 600;
}
.mine__info {
  margin-left: 28rpx;
  flex: 1;
  min-width: 0;
}
.mine__name {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
}
.mine__sub {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.86);
}
.mine__roles {
  margin-top: 12rpx;
  display: flex;
  flex-wrap: wrap;
}
.mine__role {
  margin-right: 12rpx;
  padding: 4rpx 14rpx;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 8rpx;
  font-size: 20rpx;
}
.mine__card {
  margin: 20rpx 24rpx;
  padding: 8rpx 28rpx;
  background: $oa-card;
  border-radius: $oa-radius;
}
.mine__link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.mine__link:last-child {
  border-bottom: none;
}
.mine__link-name {
  font-size: 27rpx;
  color: $oa-text;
}
.mine__link-arrow {
  color: $oa-text-muted;
  font-size: 32rpx;
}
.mine__logout {
  margin: 40rpx 24rpx 0;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  background: $oa-card;
  border-radius: $oa-radius;
  color: $oa-danger;
  font-size: 28rpx;
}
.mine__version {
  display: block;
  margin-top: 24rpx;
  text-align: center;
  font-size: 22rpx;
  color: $oa-text-muted;
}
</style>

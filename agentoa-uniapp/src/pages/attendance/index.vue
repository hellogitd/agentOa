<template>
  <view class="at">
    <view class="at__hero">
      <text class="at__date">{{ todayText }}</text>
      <text class="at__clock">{{ clock }}</text>
      <text class="at__status">{{ statusText }}</text>

      <view class="at__punch-row">
        <view class="at__punch-col">
          <text class="at__punch-label">上班</text>
          <text class="at__punch-time">{{ inTime || '--:--' }}</text>
        </view>
        <view class="at__punch-divider" />
        <view class="at__punch-col">
          <text class="at__punch-label">下班</text>
          <text class="at__punch-time">{{ outTime || '--:--' }}</text>
        </view>
      </view>

      <button
        class="at__punch-btn"
        :disabled="submitting"
        :loading="submitting"
        @click="doPunch"
      >
        {{ submitting ? '提交中…' : punchButton }}
      </button>
      <button class="at__field-btn" :disabled="submitting" @click="doFieldPunch">外勤打卡（拍照）</button>
      <text v-if="locationHint" class="at__punch-hint at__punch-hint--warn">{{ locationHint }}</text>
      <view v-if="failedAttempt" class="at__retry">
        <text class="at__retry-text">{{ failedAttempt.message }}</text>
        <button class="at__retry-btn" :disabled="submitting" @click="retryPunch">重试</button>
      </view>
      <text class="at__punch-hint">打卡时间为服务器接收时间；重复提交由幂等键去重，原始记录一律保留。</text>
    </view>

    <view class="at__card">
      <text class="at__card-title">今日汇总</text>
      <view class="at__stats">
        <view class="at__stat">
          <text class="at__stat-value">{{ fmtMinutes(summary.scheduledMinutes) }}</text>
          <text class="at__stat-label">应出勤</text>
        </view>
        <view class="at__stat">
          <text class="at__stat-value">{{ fmtMinutes(summary.workedMinutes) }}</text>
          <text class="at__stat-label">实际工时</text>
        </view>
        <view class="at__stat">
          <text class="at__stat-value">{{ summary.lateMinutes || 0 }}</text>
          <text class="at__stat-label">迟到（分钟）</text>
        </view>
        <view class="at__stat">
          <text class="at__stat-value">{{ summary.earlyMinutes || 0 }}</text>
          <text class="at__stat-label">早退（分钟）</text>
        </view>
      </view>
      <view v-if="summary.isAbnormal" class="at__abnormal">
        <text>异常：{{ summary.abnormalReason || '存在考勤异常' }}</text>
      </view>
    </view>

    <view class="at__card">
      <text class="at__card-title">快捷入口</text>
      <view class="at__links">
        <view class="at__link" @click="go('/pages/attendance/records')">
          <text class="at__link-name">打卡记录</text>
          <text class="at__link-arrow">›</text>
        </view>
        <view class="at__link" @click="go('/pages/attendance/balance')">
          <text class="at__link-name">假期余额</text>
          <text class="at__link-arrow">›</text>
        </view>
        <view class="at__link" @click="goApply('correction')">
          <text class="at__link-name">补卡申请</text>
          <text class="at__link-arrow">›</text>
        </view>
        <view class="at__link" @click="goApply('leave')">
          <text class="at__link-name">请假申请</text>
          <text class="at__link-arrow">›</text>
        </view>
        <view class="at__link" @click="goApply('overtime')">
          <text class="at__link-name">加班申请</text>
          <text class="at__link-arrow">›</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { punch, punchToday, dayToday } from '@/api/attendance'
import { uploadPrivateFile } from '@/api/file'
import { formatDate, weekday, formatMinutes, formatDateTime, pad2 } from '@/utils/format'
import { go as navGo } from '@/utils/nav'
import { openQuickApply } from '@/utils/quick-apply'
import { getLocation, guideEnableLocation } from '@/utils/location'
import { isMpWeixin } from '@/utils/platform'
import { newIdempotencyKey } from '@/utils/idempotency'

export default {
  data() {
    return {
      clock: '',
      timer: null,
      submitting: false,
      summary: {},
      today: {},
      locationHint: '',
      failedAttempt: null
    }
  },
  computed: {
    todayText() {
      const d = new Date()
      return `${formatDate(d)} ${weekday(d)}`
    },
    inTime() {
      return this.summary.punchInTime ? formatDateTime(this.summary.punchInTime).slice(11) : ''
    },
    outTime() {
      return this.summary.punchOutTime ? formatDateTime(this.summary.punchOutTime).slice(11) : ''
    },
    statusText() {
      if (this.summary.isAbnormal) return '今日存在考勤异常'
      if (this.inTime && this.outTime) return '今日已完成上/下班打卡'
      if (this.inTime) return '已上班打卡，记得下班打卡'
      return '尚未打卡'
    },
    punchButton() {
      if (!this.inTime) return '上班打卡'
      if (!this.outTime) return '下班打卡'
      return '已完成打卡'
    }
  },
  onShow() {
    this.tick()
    this.timer = setInterval(() => this.tick(), 1000)
    this.refresh()
  },
  onHide() {
    this.clearTimer()
  },
  onUnload() {
    this.clearTimer()
  },
  onPullDownRefresh() {
    this.refresh().finally(() => uni.stopPullDownRefresh())
  },
  methods: {
    fmtMinutes: formatMinutes,
    tick() {
      const d = new Date()
      this.clock = `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
    },
    clearTimer() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    },
    go(url) {
      navGo(url)
    },
    async goApply(type) {
      await openQuickApply(type)
    },
    sourceCode() {
      return isMpWeixin() ? 3 : 2
    },
    deviceText() {
      const info = uni.getSystemInfoSync()
      return `${info.brand || ''} ${info.model || ''} ${info.system || ''}`.trim()
    },
    async collectLocation(required) {
      const loc = await getLocation()
      if (loc.ok) {
        this.locationHint = ''
        return loc
      }
      if (loc.denied) {
        this.locationHint = required ? '未获得定位权限，外勤打卡需要定位与现场照片' : '未获得定位权限，本次打卡将按 IP 规则判定'
        if (required) guideEnableLocation()
      } else {
        this.locationHint = required ? '定位获取失败，外勤打卡需要定位与现场照片' : '定位获取失败，本次打卡将按 IP 规则判定'
      }
      return loc
    },
    buildPunchPayload(punchType, loc, photoFileId) {
      const payload = { punchType, source: this.sourceCode(), device: this.deviceText() }
      if (loc && loc.ok) {
        payload.lng = loc.lng
        payload.lat = loc.lat
        if (loc.accuracy != null) payload.accuracyMeters = loc.accuracy
      }
      if (photoFileId) payload.photoFileId = photoFileId
      return payload
    },
    pickFieldPhoto() {
      return new Promise((resolve) => {
        uni.chooseImage({
          count: 1,
          sizeType: ['compressed'],
          sourceType: ['camera', 'album'],
          success: async (res) => {
            const filePath = res.tempFilePaths && res.tempFilePaths[0]
            if (!filePath) {
              resolve(null)
              return
            }
            uni.showLoading({ title: '照片上传中…' })
            try {
              const file = await uploadPrivateFile(filePath)
              resolve(file && file.fileId)
            } catch (e) {
              resolve(null)
            } finally {
              uni.hideLoading()
            }
          },
          fail: () => resolve(null)
        })
      })
    },
    async doPunch() {
      if (this.submitting) return
      if (this.inTime && this.outTime) {
        uni.showToast({ title: '今日已完成上/下班打卡', icon: 'none' })
        return
      }
      const punchType = this.inTime ? 2 : 1
      const loc = await this.collectLocation(false)
      const payload = this.buildPunchPayload(punchType, loc)
      await this.submitPunch(payload, null, `${punchType === 1 ? '上班' : '下班'}打卡`)
    },
    async doFieldPunch() {
      if (this.submitting) return
      const loc = await this.collectLocation(true)
      if (!loc.ok) return
      const photoFileId = await this.pickFieldPhoto()
      if (!photoFileId) return
      const payload = this.buildPunchPayload(3, loc, photoFileId)
      await this.submitPunch(payload, null, '外勤打卡')
    },
    async submitPunch(payload, reuseKey, label) {
      const key = reuseKey || newIdempotencyKey()
      this.submitting = true
      try {
        const res = await punch(payload, key)
        this.failedAttempt = null
        uni.showToast({ title: this.resultText(label, res), icon: 'success', duration: 2500 })
        await this.refresh()
      } catch (e) {
        this.failedAttempt = {
          payload,
          key,
          message: (e && e.msg) || '网络异常，打卡未提交（可重试，不会重复打卡）'
        }
      } finally {
        this.submitting = false
      }
    },
    retryPunch() {
      if (!this.failedAttempt || this.submitting) return
      const attempt = this.failedAttempt
      this.submitPunch(attempt.payload, attempt.key, '打卡')
    },
    resultText(label, res) {
      const bits = [`${label}成功`]
      if (res && res.isLate && res.lateMinutes > 0) bits.push(`迟到 ${res.lateMinutes} 分钟`)
      if (res && res.isEarly && res.earlyMinutes > 0) bits.push(`早退 ${res.earlyMinutes} 分钟`)
      return bits.join('，')
    },
    async refresh() {
      try {
        const [day, today] = await Promise.all([dayToday().catch(() => null), punchToday().catch(() => null)])
        this.today = today || {}
        this.summary = { ...(today || {}), ...(day || {}) }
      } catch (e) {
        this.summary = {}
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.at {
  padding-bottom: 40rpx;
}
.at__hero {
  margin: 20rpx 24rpx;
  padding: 40rpx 32rpx;
  background: linear-gradient(150deg, $oa-primary 0%, #4b83f8 100%);
  border-radius: 24rpx;
  color: #ffffff;
  text-align: center;
}
.at__date {
  display: block;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.86);
}
.at__clock {
  display: block;
  margin-top: 12rpx;
  font-size: 76rpx;
  font-weight: 700;
  letter-spacing: 4rpx;
}
.at__status {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.86);
}
.at__punch-row {
  margin-top: 32rpx;
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.16);
  border-radius: 18rpx;
  padding: 24rpx 0;
}
.at__punch-col {
  flex: 1;
  text-align: center;
}
.at__punch-label {
  display: block;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.8);
}
.at__punch-time {
  display: block;
  margin-top: 8rpx;
  font-size: 34rpx;
  font-weight: 600;
}
.at__punch-divider {
  width: 1rpx;
  height: 56rpx;
  background: rgba(255, 255, 255, 0.28);
}
.at__punch-btn {
  margin-top: 32rpx;
  height: 92rpx;
  line-height: 92rpx;
  background: #ffffff;
  color: $oa-primary;
  font-size: 32rpx;
  border-radius: 46rpx;
  border: none;
}
.at__punch-btn::after {
  border: none;
}
.at__punch-btn[disabled] {
  background: rgba(255, 255, 255, 0.6);
  color: $oa-primary;
}
.at__field-btn {
  margin-top: 16rpx;
  height: 76rpx;
  line-height: 76rpx;
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  font-size: 26rpx;
  border-radius: 38rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.5);
}
.at__field-btn::after {
  border: none;
}
.at__field-btn[disabled] {
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.7);
}
.at__retry {
  margin-top: 18rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.16);
  border-radius: 14rpx;
  padding: 14rpx 18rpx;
}
.at__retry-text {
  flex: 1;
  font-size: 22rpx;
  color: #ffe2d6;
  text-align: left;
}
.at__retry-btn {
  margin-left: 16rpx;
  height: 56rpx;
  line-height: 56rpx;
  padding: 0 28rpx;
  background: #ffffff;
  color: $oa-danger;
  font-size: 24rpx;
  border-radius: 28rpx;
  border: none;
}
.at__retry-btn::after {
  border: none;
}
.at__punch-hint--warn {
  color: #ffe2d6;
}
.at__punch-hint {
  display: block;
  margin-top: 18rpx;
  font-size: 21rpx;
  color: rgba(255, 255, 255, 0.72);
  line-height: 1.7;
}
.at__card {
  margin: 20rpx 24rpx;
  padding: 24rpx 28rpx;
  background: $oa-card;
  border-radius: $oa-radius;
}
.at__card-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: $oa-text;
}
.at__stats {
  display: flex;
  margin-top: 20rpx;
}
.at__stat {
  flex: 1;
  text-align: center;
}
.at__stat-value {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: $oa-text;
}
.at__stat-label {
  display: block;
  margin-top: 8rpx;
  font-size: 21rpx;
  color: $oa-text-muted;
}
.at__abnormal {
  margin-top: 20rpx;
  padding: 16rpx 20rpx;
  background: #fdeceb;
  border-radius: 12rpx;
  color: $oa-danger;
  font-size: 24rpx;
}
.at__links {
  margin-top: 12rpx;
}
.at__link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 26rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.at__link:last-child {
  border-bottom: none;
}
.at__link-name {
  font-size: 27rpx;
  color: $oa-text;
}
.at__link-arrow {
  color: $oa-text-muted;
  font-size: 32rpx;
}
</style>

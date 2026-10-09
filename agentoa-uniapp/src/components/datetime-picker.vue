<template>
  <view class="dt-picker">
    <text class="dt-picker__label">{{ label }}<text v-if="required" class="dt-picker__req">*</text></text>
    <view class="dt-picker__control">
      <picker mode="date" :value="dateValue" @change="onDate">
        <view class="dt-picker__box" :class="{ 'dt-picker__box--empty': !dateValue }">
          {{ dateValue || '选择日期' }}
        </view>
      </picker>
      <picker v-if="!dateOnly" mode="time" :value="timeValue" @change="onTime">
        <view class="dt-picker__box dt-picker__box--time" :class="{ 'dt-picker__box--empty': !timeValue }">
          {{ timeValue || '选择时间' }}
        </view>
      </picker>
    </view>
  </view>
</template>

<script>
import { formatDate, formatTime } from '@/utils/format'

export default {
  name: 'DatetimePicker',
  props: {
    label: { type: String, default: '' },
    /** yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss */
    value: { type: String, default: '' },
    dateOnly: { type: Boolean, default: false },
    required: { type: Boolean, default: false }
  },
  computed: {
    dateValue() {
      return this.value ? formatDate(this.value) : ''
    },
    timeValue() {
      if (this.dateOnly || !this.value) return ''
      return formatTime(this.value)
    }
  },
  methods: {
    emitChange(date, time) {
      const d = date || this.dateValue
      if (!d) return
      if (this.dateOnly) {
        this.$emit('change', d)
        return
      }
      const t = time || this.timeValue || '00:00'
      this.$emit('change', `${d} ${t}:00`)
    },
    onDate(e) {
      this.emitChange(e.detail.value, this.timeValue)
    },
    onTime(e) {
      this.emitChange(this.dateValue, e.detail.value)
    }
  }
}
</script>

<style lang="scss" scoped>
.dt-picker {
  padding: 20rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.dt-picker:last-child {
  border-bottom: none;
}
.dt-picker__label {
  display: block;
  font-size: 24rpx;
  color: $oa-text-secondary;
  margin-bottom: 12rpx;
}
.dt-picker__req {
  color: $oa-danger;
  margin-left: 6rpx;
}
.dt-picker__control {
  display: flex;
}
.dt-picker__box {
  flex: 1;
  height: 76rpx;
  line-height: 76rpx;
  padding: 0 20rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: $oa-text;
}
.dt-picker__box--time {
  flex: none;
  width: 200rpx;
  margin-left: 16rpx;
  text-align: center;
}
.dt-picker__box--empty {
  color: $oa-text-muted;
}
</style>

<template>
  <view class="oa-cell" :class="{ 'oa-cell--border': border }">
    <text class="oa-cell__label">{{ label }}</text>
    <view class="oa-cell__value">
      <slot>
        <text class="oa-cell__text" :class="{ 'oa-cell__text--muted': !value }">{{ value || '-' }}</text>
      </slot>
    </view>
  </view>
</template>

<script>
export default {
  name: 'OaCell',
  props: {
    label: { type: String, default: '' },
    value: { type: [String, Number], default: '' },
    border: { type: Boolean, default: true }
  }
}
</script>

<style lang="scss" scoped>
.oa-cell {
  display: flex;
  align-items: flex-start;
  padding: 20rpx 0;
}
.oa-cell--border {
  border-bottom: 1rpx solid $oa-border;
}
.oa-cell:last-child {
  border-bottom: none;
}
.oa-cell__label {
  width: 180rpx;
  flex-shrink: 0;
  color: $oa-text-secondary;
  font-size: 26rpx;
}
.oa-cell__value {
  flex: 1;
  min-width: 0;
  text-align: right;
}
.oa-cell__text {
  color: $oa-text;
  font-size: 26rpx;
  word-break: break-all;
}
.oa-cell__text--muted {
  color: $oa-text-muted;
}
</style>

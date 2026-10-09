<template>
  <view class="oa-card">
    <view v-if="title" class="oa-card__head">
      <text class="oa-card__title">{{ title }}</text>
      <slot name="extra" />
    </view>
    <view class="oa-card__body">
      <slot />
    </view>
  </view>
</template>

<script>
export default {
  name: 'OaCard',
  props: {
    title: { type: String, default: '' }
  }
}
</script>

<style lang="scss" scoped>
.oa-card {
  margin: 20rpx 24rpx;
  padding: 8rpx 28rpx 8rpx;
  background: $oa-card;
  border-radius: $oa-radius;
  box-shadow: 0 2rpx 12rpx rgba(31, 36, 48, 0.04);
}
.oa-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0 8rpx;
}
.oa-card__title {
  font-size: 28rpx;
  font-weight: 600;
  color: $oa-text;
}
.oa-card__body {
  padding-bottom: 16rpx;
}
</style>

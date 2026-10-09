<template>
  <view class="oa-empty">
    <text class="oa-empty__text">{{ text }}</text>
    <text v-if="hint" class="oa-empty__hint">{{ hint }}</text>
  </view>
</template>

<script>
export default {
  name: 'OaEmpty',
  props: {
    text: { type: String, default: '暂无数据' },
    hint: { type: String, default: '' }
  }
}
</script>

<style lang="scss" scoped>
.oa-empty {
  padding: 60rpx 40rpx;
  text-align: center;
}
.oa-empty__text {
  display: block;
  color: $oa-text-muted;
  font-size: 26rpx;
}
.oa-empty__hint {
  display: block;
  margin-top: 12rpx;
  color: $oa-text-muted;
  font-size: 24rpx;
}
</style>

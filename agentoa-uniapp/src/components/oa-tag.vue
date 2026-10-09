<template>
  <text class="oa-tag" :class="`oa-tag--${tone}`">{{ text }}</text>
</template>

<script>
export default {
  name: 'OaTag',
  props: {
    text: { type: String, default: '' },
    tone: {
      type: String,
      default: 'default',
      validator: (v) => ['default', 'primary', 'success', 'warning', 'danger'].includes(String(v))
    }
  }
}
</script>

<style lang="scss" scoped>
.oa-tag {
  display: inline-block;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  line-height: 1.5;
  white-space: nowrap;
}
.oa-tag--default {
  background: #f0f2f7;
  color: $oa-text-secondary;
}
.oa-tag--primary {
  background: $oa-primary-light;
  color: $oa-primary;
}
.oa-tag--success {
  background: #e6f6ee;
  color: $oa-success;
}
.oa-tag--warning {
  background: #fdf3e2;
  color: #c98a22;
}
.oa-tag--danger {
  background: #fdeceb;
  color: $oa-danger;
}
</style>

<template>
  <view class="tf">
    <text class="tf__label">{{ label }}<text v-if="required" class="tf__req">*</text></text>
    <textarea
      v-if="textarea"
      class="tf__area"
      :value="value"
      :placeholder="placeholder"
      :maxlength="maxlength"
      :disabled="disabled"
      @input="onInput"
    />
    <input
      v-else
      class="tf__input"
      :value="value"
      :type="type"
      :password="password"
      :placeholder="placeholder"
      :maxlength="maxlength"
      :disabled="disabled"
      @input="onInput"
    />
  </view>
</template>

<script>
export default {
  name: 'TextField',
  props: {
    label: { type: String, default: '' },
    value: { type: [String, Number], default: '' },
    placeholder: { type: String, default: '请输入' },
    type: { type: String, default: 'text' },
    textarea: { type: Boolean, default: false },
    password: { type: Boolean, default: false },
    required: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
    maxlength: { type: Number, default: 200 }
  },
  methods: {
    onInput(e) {
      this.$emit('change', e.detail.value)
    }
  }
}
</script>

<style lang="scss" scoped>
.tf {
  padding: 20rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.tf:last-child {
  border-bottom: none;
}
.tf__label {
  display: block;
  font-size: 24rpx;
  color: $oa-text-secondary;
  margin-bottom: 12rpx;
}
.tf__req {
  color: $oa-danger;
  margin-left: 6rpx;
}
.tf__input {
  height: 76rpx;
  padding: 0 20rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 28rpx;
}
.tf__area {
  width: 100%;
  height: 180rpx;
  padding: 16rpx 20rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 28rpx;
}
</style>

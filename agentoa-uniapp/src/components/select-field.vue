<template>
  <view class="sf">
    <text class="sf__label">{{ label }}<text v-if="required" class="sf__req">*</text></text>
    <picker mode="selector" :range="labels" :value="index" @change="onChange">
      <view class="sf__box" :class="{ 'sf__box--empty': index < 0 }">
        {{ index >= 0 ? labels[index] : placeholder }}
      </view>
    </picker>
  </view>
</template>

<script>
export default {
  name: 'SelectField',
  props: {
    label: { type: String, default: '' },
    /** [{ value, label }] 或 字符串数组 */
    options: { type: Array, default: () => [] },
    value: { type: [String, Number], default: '' },
    placeholder: { type: String, default: '请选择' },
    required: { type: Boolean, default: false }
  },
  computed: {
    normalized() {
      return this.options.map((it) => {
        if (it && typeof it === 'object') {
          const item = /** @type {{ value?: any, label?: any }} */ (it)
          return { value: item.value, label: item.label == null ? '' : String(item.label) }
        }
        return { value: it, label: String(it) }
      })
    },
    labels() {
      return this.normalized.map((it) => it.label)
    },
    index() {
      return this.normalized.findIndex((it) => String(it.value) === String(this.value))
    }
  },
  methods: {
    onChange(e) {
      const idx = Number(e.detail.value)
      const item = this.normalized[idx]
      if (item) this.$emit('change', item.value)
    }
  }
}
</script>

<style lang="scss" scoped>
.sf {
  padding: 20rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.sf:last-child {
  border-bottom: none;
}
.sf__label {
  display: block;
  font-size: 24rpx;
  color: $oa-text-secondary;
  margin-bottom: 12rpx;
}
.sf__req {
  color: $oa-danger;
  margin-left: 6rpx;
}
.sf__box {
  height: 76rpx;
  line-height: 76rpx;
  padding: 0 20rpx;
  background: #f5f7fb;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: $oa-text;
}
.sf__box--empty {
  color: $oa-text-muted;
}
</style>

<template>
  <view class="af">
    <text class="af__label">{{ label }}<text v-if="required" class="af__req">*</text></text>
    <view v-if="preview" class="af__row">
      <image class="af__thumb" :src="preview" mode="aspectFill" @click="viewFull" />
      <text class="af__remove" @click="clear">删除</text>
    </view>
    <view v-else-if="fileName" class="af__row">
      <text class="af__file-name">{{ fileName }}</text>
      <text class="af__remove" @click="clear">删除</text>
    </view>
    <view v-else class="af__pick" @click="pick">{{ imageOnly ? '拍照 / 选择图片' : '选择文件' }}</view>
    <text v-if="hint" class="af__hint">{{ hint }}</text>
  </view>
</template>

<script>
import { uploadPrivateFile, previewImage } from '@/api/file'
import { chooseAttachmentFile, chooseImageFile } from '@/utils/picker'

/** 表单附件字段（docs/23 H5-H3-01）：上传私有文件，值 = sys_file fileId 字符串 */
export default {
  name: 'AttachmentField',
  props: {
    label: { type: String, default: '' },
    value: { type: [String, Number], default: '' },
    required: { type: Boolean, default: false },
    imageOnly: { type: Boolean, default: false },
    hint: { type: String, default: '' }
  },
  data() {
    return {
      preview: '',
      fileName: '',
      uploading: false
    }
  },
  watch: {
    value: {
      immediate: true,
      handler(v) {
        if (!v) {
          this.preview = ''
          this.fileName = ''
          return
        }
        if (this.imageOnly && !this.preview) {
          previewImage(String(v))
            .then((uri) => {
              this.preview = uri
            })
            .catch(() => {})
        }
      }
    }
  },
  methods: {
    async pick() {
      if (this.uploading) return
      const paths = this.imageOnly ? await chooseImageFile(1) : await chooseAttachmentFile(1)
      const filePath = paths[0]
      if (!filePath) return
      this.uploading = true
      uni.showLoading({ title: '上传中…' })
      try {
        const file = await uploadPrivateFile(filePath)
        if (this.imageOnly) this.preview = filePath
        this.fileName = (file && file.fileName) || ''
        this.$emit('change', file && file.fileId ? String(file.fileId) : '')
      } catch (e) {
        this.$emit('change', '')
      } finally {
        uni.hideLoading()
        this.uploading = false
      }
    },
    clear() {
      this.preview = ''
      this.fileName = ''
      this.$emit('change', '')
    },
    viewFull() {
      if (this.preview) uni.previewImage({ urls: [this.preview] })
    }
  }
}
</script>

<style lang="scss" scoped>
.af {
  padding: 20rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.af:last-child {
  border-bottom: none;
}
.af__label {
  display: block;
  font-size: 24rpx;
  color: $oa-text-secondary;
  margin-bottom: 12rpx;
}
.af__req {
  color: $oa-danger;
  margin-left: 6rpx;
}
.af__pick {
  height: 76rpx;
  line-height: 76rpx;
  text-align: center;
  background: #f5f7fb;
  border: 1rpx dashed $oa-border;
  border-radius: 12rpx;
  font-size: 26rpx;
  color: $oa-primary;
}
.af__row {
  display: flex;
  align-items: center;
}
.af__thumb {
  width: 140rpx;
  height: 140rpx;
  border-radius: 12rpx;
  background: #f5f7fb;
}
.af__file-name {
  flex: 1;
  font-size: 26rpx;
  color: $oa-text;
}
.af__remove {
  margin-left: 20rpx;
  font-size: 24rpx;
  color: $oa-danger;
}
.af__hint {
  display: block;
  margin-top: 8rpx;
  font-size: 20rpx;
  color: $oa-text-muted;
}
</style>

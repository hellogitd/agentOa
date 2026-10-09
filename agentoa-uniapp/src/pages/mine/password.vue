<template>
  <view class="pw">
    <view class="pw__card">
      <view v-if="first" class="pw__notice">
        检测到您使用初始密码登录，或管理员要求更新密码。请设置 12–72 字节的新密码后继续。
      </view>

      <text-field label="当前密码" :value="form.oldPassword" password required placeholder="请输入当前密码"
        :maxlength="72" @change="(v) => set('oldPassword', v)" />
      <text-field label="新密码" :value="form.newPassword" password required placeholder="12–72 字节，与当前密码不同"
        :maxlength="72" @change="(v) => set('newPassword', v)" />
      <text-field label="确认新密码" :value="form.confirmPassword" password required placeholder="请再次输入新密码"
        :maxlength="72" @change="(v) => set('confirmPassword', v)" />

      <text class="pw__rule">规则：长度 12–72 个 UTF-8 字节，且不能与当前密码相同；提交后所有会话将失效需重新登录。</text>

      <button class="pw__submit" :disabled="submitting" :loading="submitting" @click="submit">
        {{ submitting ? '提交中…' : '确认修改' }}
      </button>
    </view>
  </view>
</template>

<script>
import { changePassword } from '@/api/auth'
import { useUserStore } from '@/store/user'
import { safeReLaunch } from '@/utils/auth'
import TextField from '@/components/text-field.vue'

function byteLength(s) {
  let n = 0
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i)
    if (c < 0x80) n += 1
    else if (c < 0x800) n += 2
    else if (c >= 0xd800 && c <= 0xdbff) {
      n += 4
      i++
    } else n += 3
  }
  return n
}

export default {
  components: { TextField },
  data() {
    return {
      first: false,
      submitting: false,
      form: { oldPassword: '', newPassword: '', confirmPassword: '' }
    }
  },
  onLoad(options) {
    this.first = !!(options && options.first)
  },
  onBackPress() {
    return this.first
  },
  methods: {
    set(key, value) {
      this.form[key] = value
    },
    async submit() {
      if (this.submitting) return
      const f = this.form
      if (!f.oldPassword || !f.newPassword || !f.confirmPassword) {
        uni.showToast({ title: '请完整填写密码', icon: 'none' })
        return
      }
      const bytes = byteLength(f.newPassword)
      if (bytes < 12 || bytes > 72) {
        uni.showToast({ title: '新密码需 12–72 个 UTF-8 字节', icon: 'none' })
        return
      }
      if (f.newPassword === f.oldPassword) {
        uni.showToast({ title: '新密码不能与当前密码相同', icon: 'none' })
        return
      }
      if (f.newPassword !== f.confirmPassword) {
        uni.showToast({ title: '两次输入的新密码不一致', icon: 'none' })
        return
      }
      this.submitting = true
      try {
        await changePassword(f.oldPassword, f.newPassword)
        useUserStore().reset()
        uni.showToast({ title: '密码已修改，请重新登录', icon: 'success' })
        setTimeout(() => {
          safeReLaunch('/pages/login/login')
        }, 600)
      } catch (e) {
        // 统一错误提示
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.pw {
  padding-bottom: 40rpx;
}
.pw__card {
  margin: 20rpx 24rpx;
  padding: 24rpx 28rpx;
  background: $oa-card;
  border-radius: $oa-radius;
}
.pw__notice {
  margin-bottom: 16rpx;
  padding: 18rpx 20rpx;
  background: #fdf3e2;
  color: #c98a22;
  border-radius: 12rpx;
  font-size: 24rpx;
  line-height: 1.7;
}
.pw__rule {
  display: block;
  margin-top: 20rpx;
  font-size: 22rpx;
  color: $oa-text-muted;
  line-height: 1.8;
}
.pw__submit {
  margin-top: 32rpx;
  height: 88rpx;
  line-height: 88rpx;
  background: $oa-primary;
  color: #ffffff;
  font-size: 29rpx;
  border-radius: 44rpx;
  border: none;
}
.pw__submit::after {
  border: none;
}
.pw__submit[disabled] {
  background: #a9c1f7;
  color: #ffffff;
}
</style>

<template>
  <view class="login">
    <view class="login__hero" :style="{ paddingTop: statusBarHeight + 40 + 'px' }">
      <text class="login__logo">AgentOA</text>
      <text class="login__slogan">移动办公 · 审批 / 打卡 / 公告</text>
    </view>

    <view class="login__panel">
      <view class="login__field">
        <text class="login__label">账号</text>
        <input
          v-model="form.username"
          class="login__input"
          placeholder="请输入账号"
          placeholder-class="login__placeholder"
          :disabled="submitting"
          maxlength="30"
        />
      </view>

      <view class="login__field">
        <text class="login__label">密码</text>
        <input
          v-model="form.password"
          class="login__input"
          password
          placeholder="请输入密码"
          placeholder-class="login__placeholder"
          :disabled="submitting"
          maxlength="72"
        />
      </view>

      <view v-if="captchaEnabled" class="login__field">
        <text class="login__label">验证码</text>
        <view class="login__captcha-row">
          <input
            v-model="form.captchaCode"
            class="login__input login__input--captcha"
            placeholder="请输入验证码"
            placeholder-class="login__placeholder"
            :disabled="submitting"
            maxlength="6"
          />
          <view class="login__captcha-img" @click="loadCaptcha">
            <image v-if="captchaImg" class="login__captcha-img-inner" :src="captchaImg" mode="aspectFit" />
            <text v-else class="login__captcha-tip">点击刷新</text>
          </view>
        </view>
      </view>

      <button
        class="login__submit"
        :disabled="submitting || !canSubmit"
        :loading="submitting"
        @click="handleLogin"
      >
        {{ submitting ? '登录中…' : '登 录' }}
      </button>

      <text class="login__tip">密码经 HTTPS 提交，服务端校验；首次登录后请按提示修改密码。</text>
    </view>
  </view>
</template>

<script>
import { login, captcha } from '@/api/auth'
import { useUserStore } from '@/store/user'
import { safeReLaunch } from '@/utils/auth'

export default {
  data() {
    return {
      statusBarHeight: 20,
      submitting: false,
      captchaEnabled: false,
      captchaImg: '',
      captchaUuid: '',
      form: { username: '', password: '', captchaCode: '' }
    }
  },
  computed: {
    canSubmit() {
      if (!this.form.username || !this.form.password) return false
      if (this.captchaEnabled && !this.form.captchaCode) return false
      return true
    }
  },
  onLoad() {
    const info = uni.getSystemInfoSync()
    this.statusBarHeight = info.statusBarHeight || 20
    this.loadCaptcha()
  },
  methods: {
    async loadCaptcha() {
      try {
        const data = await captcha()
        this.captchaEnabled = !!data.captchaEnabled
        this.captchaUuid = data.uuid || ''
        let img = data.img || ''
        if (img && img.indexOf('data:') !== 0) {
          img = 'data:image/gif;base64,' + img
        }
        this.captchaImg = img
      } catch (e) {
        this.captchaEnabled = false
      }
    },
    async handleLogin() {
      if (this.submitting || !this.canSubmit) return
      this.submitting = true
      try {
        const payload = await login(
          this.form.username.trim(),
          this.form.password,
          this.captchaEnabled ? this.form.captchaCode.trim() : undefined,
          this.captchaEnabled ? this.captchaUuid : undefined
        )
        const store = useUserStore()
        store.applyLogin(payload)
        if (payload && payload.user && payload.user.mustChangePassword) {
          uni.showToast({ title: '首次登录请修改密码', icon: 'none' })
          setTimeout(() => {
            safeReLaunch('/pages/mine/password?first=1')
          }, 400)
          return
        }
        safeReLaunch('/pages/workbench/index')
      } catch (e) {
        this.form.password = ''
        this.form.captchaCode = ''
        this.loadCaptcha()
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.login {
  min-height: 100vh;
  background: linear-gradient(180deg, $oa-primary 0%, #4b83f8 38%, $oa-bg 38%, $oa-bg 100%);
}
.login__hero {
  padding-left: 56rpx;
  padding-right: 56rpx;
  padding-bottom: 120rpx;
}
.login__logo {
  display: block;
  font-size: 60rpx;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 2rpx;
}
.login__slogan {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.86);
}
.login__panel {
  margin: -80rpx 40rpx 0;
  padding: 40rpx 40rpx 48rpx;
  background: $oa-card;
  border-radius: 24rpx;
  box-shadow: 0 12rpx 40rpx rgba(31, 36, 48, 0.1);
}
.login__field {
  padding: 22rpx 0;
  border-bottom: 1rpx solid $oa-border;
}
.login__label {
  display: block;
  font-size: 24rpx;
  color: $oa-text-secondary;
  margin-bottom: 10rpx;
}
.login__input {
  width: 100%;
  height: 64rpx;
  font-size: 30rpx;
  color: $oa-text;
}
.login__placeholder {
  color: $oa-text-muted;
}
.login__captcha-row {
  display: flex;
  align-items: center;
}
.login__input--captcha {
  flex: 1;
}
.login__captcha-img {
  width: 180rpx;
  height: 64rpx;
  margin-left: 20rpx;
  background: #eef2fb;
  border-radius: 10rpx;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.login__captcha-img-inner {
  width: 100%;
  height: 100%;
}
.login__captcha-tip {
  font-size: 22rpx;
  color: $oa-text-muted;
}
.login__submit {
  margin-top: 48rpx;
  height: 92rpx;
  line-height: 92rpx;
  background: $oa-primary;
  color: #ffffff;
  font-size: 32rpx;
  border-radius: 46rpx;
  border: none;
}
.login__submit[disabled] {
  background: #a9c1f7;
  color: #ffffff;
}
.login__tip {
  display: block;
  margin-top: 24rpx;
  font-size: 22rpx;
  color: $oa-text-muted;
  line-height: 1.7;
}
</style>

<script>
import { getToken, redirectToLogin } from '@/utils/auth'
import { connectRealtime } from '@/utils/realtime'

export default {
  onLaunch() {
    // 先于首个页面 onShow 执行，避免未登录页面发起无效请求
    if (!getToken()) {
      redirectToLogin()
      return
    }
    connectRealtime()
  },
  onShow() {
    // 切后台回前台：重连实时通道（断线期间页面拉取兜底，docs/23 H5-H5-03）
    if (getToken()) connectRealtime()
  },
  onHide() {}
}
</script>

<style lang="scss">
page {
  background-color: $oa-bg;
  color: $oa-text;
  font-size: 28rpx;
  line-height: 1.6;
}
</style>

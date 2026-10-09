/** 平台差异判定（docs/23 §5 兼容）：编译期 UNI_PLATFORM 优先，运行时回退。 */

export function isMpWeixin() {
  if (typeof process !== 'undefined' && process.env && process.env.UNI_PLATFORM) {
    return process.env.UNI_PLATFORM === 'mp-weixin'
  }
  try {
    return uni.getSystemInfoSync().uniPlatform === 'mp-weixin'
  } catch (e) {
    return false
  }
}

export function isH5() {
  if (typeof process !== 'undefined' && process.env && process.env.UNI_PLATFORM) {
    return process.env.UNI_PLATFORM === 'web'
  }
  try {
    return uni.getSystemInfoSync().uniPlatform === 'web'
  } catch (e) {
    return true
  }
}

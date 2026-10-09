/**
 * 定位获取（docs/23 H5-H2-01）：统一返回 {ok, lng, lat, accuracy, denied, msg}。
 * denied=true 表示用户拒绝授权（需引导开启或降级 IP 规则）；其余失败按弱网/不可用处理。
 */
export function getLocation() {
  return new Promise((resolve) => {
    uni.getLocation({
      type: 'gcj02',
      altitude: false,
      isHighAccuracy: true,
      success: (res) => {
        resolve({
          ok: true,
          lng: res.longitude,
          lat: res.latitude,
          accuracy: typeof res.accuracy === 'number' ? res.accuracy : null,
          denied: false,
          msg: ''
        })
      },
      fail: (err) => {
        const msg = (err && err.errMsg) || ''
        const denied = /auth|deny|denied|permission|授权|权限/i.test(msg)
        resolve({ ok: false, lng: null, lat: null, accuracy: null, denied, msg })
      }
    })
  })
}

/** 引导开启定位权限（拒绝授权交互） */
export function guideEnableLocation() {
  uni.showModal({
    title: '未获得定位权限',
    content: '外勤打卡需要定位信息。可在系统设置中开启定位/授权后重试；上/下班打卡可继续使用，将按 IP 规则判定。',
    confirmText: '去设置',
    cancelText: '知道了',
    success: (res) => {
      if (res.confirm) {
        uni.openSetting({})
      }
    }
  })
}

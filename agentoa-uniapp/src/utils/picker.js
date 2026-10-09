/**
 * 通用文件选择（docs/23 §5 平台差异清单）：
 * 小程序走 chooseMessageFile（会话文件）；H5/App 走 chooseFile。
 * 统一返回临时路径数组，取消返回 []。
 */
export function chooseAttachmentFile(count = 1) {
  return new Promise((resolve) => {
    const done = (paths) => resolve(Array.isArray(paths) ? paths.filter(Boolean) : [])
    if (typeof uni.chooseFile === 'function' && !isMpPlatform()) {
      uni.chooseFile({
        count,
        success: (res) => done(res.tempFilePaths),
        fail: () => done([])
      })
      return
    }
    uni.chooseMessageFile({
      count,
      type: 'file',
      success: (res) => done((res.tempFiles || []).map((f) => f.path)),
      fail: () => done([])
    })
  })
}

function isMpPlatform() {
  try {
    return uni.getSystemInfoSync().uniPlatform === 'mp-weixin'
  } catch (e) {
    return false
  }
}

/** 图片单选（拍照/相册），返回临时路径或 null */
export function chooseImageFile(count = 1) {
  return new Promise((resolve) => {
    uni.chooseImage({
      count,
      sizeType: ['compressed'],
      sourceType: ['camera', 'album'],
      success: (res) => resolve(res.tempFilePaths || []),
      fail: () => resolve([])
    })
  })
}

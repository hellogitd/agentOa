const TOKEN_KEY = 'agentoa_token'
const TOKEN_TYPE_KEY = 'agentoa_token_type'
const EXPIRES_KEY = 'agentoa_token_expires'
const USER_KEY = 'agentoa_user'

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function getTokenType() {
  return uni.getStorageSync(TOKEN_TYPE_KEY) || 'Bearer'
}

export function saveSession(payload) {
  const { accessToken, tokenType = 'Bearer', expiresIn, user } = payload || {}
  if (!accessToken) return
  uni.setStorageSync(TOKEN_KEY, accessToken)
  uni.setStorageSync(TOKEN_TYPE_KEY, tokenType)
  if (expiresIn) {
    uni.setStorageSync(EXPIRES_KEY, String(Date.now() + Number(expiresIn) * 1000))
  }
  if (user) {
    uni.setStorageSync(USER_KEY, JSON.stringify(user))
  }
}

export function getUser() {
  const raw = uni.getStorageSync(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    return null
  }
}

export function setUser(user) {
  if (!user) return
  uni.setStorageSync(USER_KEY, JSON.stringify(user))
}

export function clearAuth() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(TOKEN_TYPE_KEY)
  uni.removeStorageSync(EXPIRES_KEY)
  uni.removeStorageSync(USER_KEY)
}

/** 跳转封装：uni 的 reLaunch 在并发导航时会 reject（Navigation cancelled），统一吞掉。 */
export function safeReLaunch(url) {
  try {
    const task = uni.reLaunch({ url })
    if (task && typeof task.catch === 'function') task.catch(() => {})
  } catch (e) {
    // ignore
  }
}

let redirectingToLogin = false

/** 回登录页（清会话、去重、吞掉并发导航异常）。 */
export function redirectToLogin() {
  if (redirectingToLogin) return
  redirectingToLogin = true
  clearAuth()
  safeReLaunch('/pages/login/login')
  setTimeout(() => {
    redirectingToLogin = false
  }, 800)
}

export function hasPermission(perm, perms) {
  const list = perms || (getUser() && getUser().permissions) || []
  if (!Array.isArray(list)) return false
  return list.includes('*:*:*') || list.includes(perm)
}

export function hasRole(role, roles) {
  const list = roles || (getUser() && getUser().roles) || []
  if (!Array.isArray(list)) return false
  return list.includes(role)
}

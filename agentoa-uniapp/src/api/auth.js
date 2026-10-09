import { get, post, put, unwrap } from '@/utils/request'

/** 图形验证码（docs/05 2.1）：captchaEnabled=false 时无需输入 */
export function captcha() {
  return unwrap(get('/auth/code', {}, { auth: false }))
}

/** 登录（docs/05 2.1） */
export function login(username, password, captchaCode, captchaUuid) {
  return unwrap(post('/api/v1/auth/login', { username, password, captchaCode, captchaUuid }, { auth: false }))
}

/** 登出（docs/05 2.3） */
export function logout() {
  return unwrap(post('/api/v1/auth/logout', {}, { silent: true }))
}

/** 当前用户信息（docs/05 2.4） */
export function profile() {
  return unwrap(get('/api/v1/auth/profile'))
}

/** 修改密码（docs/05 2.5），12-72 UTF-8 字节且不能与旧密码相同 */
export function changePassword(oldPassword, newPassword) {
  return unwrap(put('/api/v1/auth/password', { oldPassword, newPassword }))
}

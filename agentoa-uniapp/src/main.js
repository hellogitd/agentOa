import App from './App.vue'
import { createSSRApp } from 'vue'
import { getToken, redirectToLogin } from '@/utils/auth'

const PUBLIC_PAGES = ['/pages/login/login']

/** 全局登录守卫：非公开页面必须有会话，否则回登录页。 */
function installAuthGuard(app) {
  app.mixin({
    onShow() {
      const pages = getCurrentPages()
      const current = pages && pages[pages.length - 1]
      if (!current || !current.route) return
      const route = '/' + current.route
      if (PUBLIC_PAGES.indexOf(route) >= 0) return
      if (!getToken()) redirectToLogin()
    }
  })
}

export function createApp() {
  const app = createSSRApp(App)
  installAuthGuard(app)
  return { app }
}

/** 导航封装：tabBar 页面必须用 switchTab（不能带 query、不能 navigateTo）。 */

const TAB_PAGES = [
  '/pages/workbench/index',
  '/pages/approval/index',
  '/pages/attendance/index',
  '/pages/mine/index'
]
const APPROVAL_TAB_KEY = 'agentoa_approval_tab'

function swallow(task) {
  if (task && typeof task.catch === 'function') task.catch(() => {})
}

export function isTabPage(url) {
  return TAB_PAGES.indexOf(String(url).split('?')[0]) >= 0
}

export function go(url) {
  const raw = String(url || '')
  const path = raw.split('?')[0]
  if (isTabPage(path)) {
    try {
      swallow(uni.switchTab({ url: path }))
    } catch (e) {
      // ignore
    }
    return
  }
  try {
    swallow(uni.navigateTo({ url: raw }))
  } catch (e) {
    // ignore
  }
}

export function back() {
  try {
    swallow(uni.navigateBack())
  } catch (e) {
    // ignore
  }
}

export function redirectTo(url) {
  try {
    swallow(uni.redirectTo({ url: String(url) }))
  } catch (e) {
    // ignore
  }
}

/** 审批页签跨页传递（switchTab 不能带 query）。 */
export function openApprovalTab(tab) {
  if (tab) uni.setStorageSync(APPROVAL_TAB_KEY, tab)
  go('/pages/approval/index')
}

export function consumeApprovalTab() {
  const value = uni.getStorageSync(APPROVAL_TAB_KEY)
  if (value) uni.removeStorageSync(APPROVAL_TAB_KEY)
  return value || ''
}

export default { go, back, redirectTo, isTabPage, openApprovalTab, consumeApprovalTab }

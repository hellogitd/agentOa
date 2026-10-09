import { get, unwrap } from '@/utils/request'

/** 工作台（docs/05 10 / docs/18 步骤 5）：待办、今日打卡、公告、余额、快捷入口 */
export function workbench() {
  return unwrap(get('/api/v1/report/dashboard/workbench'))
}

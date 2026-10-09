import { listLaunchable } from '@/api/workflow'
import { go as navGo } from '@/utils/nav'

/**
 * 快捷申请预选类型（docs/23 H5-H1-02）：correction/leave/overtime 等按
 * processKey 或 businessType 解析到流程定义后直跳 apply-form；未启用则回退发起申请目录。
 */
export async function openQuickApply(type) {
  if (!type) {
    navGo('/pages/approval/apply')
    return
  }
  try {
    const data = await listLaunchable()
    const defs = (data && data.definitions) || []
    const def = defs.find((d) => d.processKey === type) || defs.find((d) => d.businessType === type)
    if (def && def.id) {
      navGo('/pages/approval/apply-form?id=' + def.id)
      return
    }
  } catch (e) {
    // 目录不可用时回退
  }
  navGo('/pages/approval/apply')
}

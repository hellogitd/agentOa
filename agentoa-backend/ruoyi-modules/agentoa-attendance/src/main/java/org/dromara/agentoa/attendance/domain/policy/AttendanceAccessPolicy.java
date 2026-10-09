package org.dromara.agentoa.attendance.domain.policy;

import java.util.Set;

/**
 * 模块 3 权限矩阵的纯函数实现（docs/13 权限与规则）。
 * <p>
 * 列表数据范围由角色 data_scope 与数据权限插件负责；这里只做动作与对象归属判断：
 * 员工仅本人，部门经理本部门，HR 维护规则与额度，导出需单独授权。
 */
public final class AttendanceAccessPolicy {

    public static final String PERM_GROUP_EDIT = "at:group:edit";
    public static final String PERM_LEAVE_QUERY = "at:leave:query";
    public static final String PERM_LEAVE_GRANT = "at:leave:grant";
    public static final String PERM_REPORT_LIST = "at:report:list";
    public static final String PERM_REPORT_EXPORT = "at:report:export";

    private AttendanceAccessPolicy() {
    }

    /** 规则维护（考勤组/班次/日历）：HR 与管理员 */
    public static boolean canMaintainRules(boolean superAdmin, Set<String> permissions) {
        return superAdmin || has(permissions, PERM_GROUP_EDIT);
    }

    /** 查询他人余额/日报：HR、部门经理（数据范围由查询参数与列表过滤共同限制） */
    public static boolean canViewOthers(boolean superAdmin, Set<String> permissions) {
        return superAdmin || has(permissions, PERM_LEAVE_QUERY) || has(permissions, PERM_REPORT_LIST);
    }

    /** 发放/调整假期额度：HR 与管理员 */
    public static boolean canGrant(boolean superAdmin, Set<String> permissions) {
        return superAdmin || has(permissions, PERM_LEAVE_GRANT);
    }

    /** 报表导出：HR 与管理员（导出遵守与列表同一授权） */
    public static boolean canExport(boolean superAdmin, Set<String> permissions) {
        return superAdmin || has(permissions, PERM_REPORT_EXPORT);
    }

    /** 单条考勤日报可见性：本人、HR、可查他人的角色 */
    public static boolean canViewDay(boolean superAdmin, Set<String> permissions, Long actorUserId, Long targetUserId) {
        if (superAdmin) {
            return true;
        }
        if (actorUserId != null && actorUserId.equals(targetUserId)) {
            return true;
        }
        return canViewOthers(superAdmin, permissions);
    }

    private static boolean has(Set<String> permissions, String perm) {
        return permissions != null && permissions.contains(perm);
    }
}

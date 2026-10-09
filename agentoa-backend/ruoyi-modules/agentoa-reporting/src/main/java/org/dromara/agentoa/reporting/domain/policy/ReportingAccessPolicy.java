package org.dromara.agentoa.reporting.domain.policy;

import java.util.Set;

/**
 * 模块 8 权限矩阵的纯函数实现（docs/18 权限过滤、docs/02 13.3 RP-07）。
 * <p>
 * 看板与导出使用同一数据范围口径：超管或 hr/finance/director 角色看全量，
 * 其余角色只看本人部门；导出在报表读权限之外单独授权，且必须与列表同一过滤口径。
 */
public final class ReportingAccessPolicy {

    public static final String PERM_REPORT_LIST = "rp:report:list";
    public static final String PERM_REPORT_EXPORT = "rp:report:export";

    public static final String ROLE_HR = "hr";
    public static final String ROLE_FINANCE = "finance";
    public static final String ROLE_DIRECTOR = "director";

    private ReportingAccessPolicy() {
    }

    /** 看板只读查询：需 rp:report:list（超管旁路）。 */
    public static boolean canViewDashboards(boolean superAdmin, Set<String> permissions) {
        return superAdmin || has(permissions, PERM_REPORT_LIST);
    }

    /** 报表导出与导出记录全量查看：需 rp:report:export（超管旁路）。 */
    public static boolean canExport(boolean superAdmin, Set<String> permissions) {
        return superAdmin || has(permissions, PERM_REPORT_EXPORT);
    }

    /** 全量数据范围：超管或 HR/财务/总监角色；其余只看本部门。 */
    public static boolean canViewAllDepartments(boolean superAdmin, Set<String> roleKeys) {
        return superAdmin || (roleKeys != null && (roleKeys.contains(ROLE_HR)
            || roleKeys.contains(ROLE_FINANCE) || roleKeys.contains(ROLE_DIRECTOR)));
    }

    /** 工作台管理视角卡片：与看板同一授权面。 */
    public static boolean canViewWorkbenchManagement(boolean superAdmin, Set<String> permissions) {
        return canViewDashboards(superAdmin, permissions);
    }

    private static boolean has(Set<String> permissions, String perm) {
        return permissions != null && permissions.contains(perm);
    }
}

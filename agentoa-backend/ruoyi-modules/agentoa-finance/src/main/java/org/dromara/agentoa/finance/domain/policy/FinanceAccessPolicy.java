package org.dromara.agentoa.finance.domain.policy;

import java.util.Set;

/**
 * 模块 4 权限矩阵的纯函数实现（docs/14 权限与安全）。
 * <p>
 * 发票明文与付款登记按 docs/14 要求显式绑定财务/出纳角色：管理员不自动获得发票明文与付款权限；
 * 员工仅本人发票与本人报销，经理按数据范围查看授权部门统计，报表导出需单独授权。
 */
public final class FinanceAccessPolicy {

    public static final String PERM_INVOICE_QUERY = "fn:invoice:query";
    public static final String PERM_PAYMENT_ADD = "fn:payment:add";
    public static final String PERM_PAYMENT_LIST = "fn:payment:list";
    public static final String PERM_REPORT_LIST = "fn:report:list";
    public static final String PERM_REPORT_EXPORT = "fn:report:export";

    public static final String ROLE_FINANCE = "finance";
    public static final String ROLE_CASHIER = "cashier";
    public static final String ROLE_DEPT_MANAGER = "dept_manager";

    private FinanceAccessPolicy() {
    }

    /** 财务/出纳角色（发票明文与付款登记的显式授权面） */
    public static boolean isFinanceRole(Set<String> roleKeys) {
        return roleKeys != null && (roleKeys.contains(ROLE_FINANCE) || roleKeys.contains(ROLE_CASHIER));
    }

    /** 发票明文：本人或财务/出纳角色（管理员不自动获得） */
    public static boolean canViewInvoice(Set<String> roleKeys, Long actorUserId, Long ownerUserId) {
        if (actorUserId != null && actorUserId.equals(ownerUserId)) {
            return true;
        }
        return isFinanceRole(roleKeys);
    }

    /** 付款登记：仅财务/出纳角色（管理员不自动获得） */
    public static boolean canRegisterPayment(Set<String> roleKeys) {
        return isFinanceRole(roleKeys);
    }

    /** 待付款单与付款记录查询：财务/出纳 */
    public static boolean canViewPayments(Set<String> roleKeys) {
        return isFinanceRole(roleKeys);
    }

    /** 报销单财务视角（待付款清单）：财务/出纳 */
    public static boolean canViewAllClaims(Set<String> roleKeys) {
        return isFinanceRole(roleKeys);
    }

    /** 费用报表：财务或经理授权（菜单权限） */
    public static boolean canViewReports(boolean superAdmin, Set<String> permissions) {
        return superAdmin || has(permissions, PERM_REPORT_LIST);
    }

    /** 报表导出：与报表同一授权之外的单独导出权限 */
    public static boolean canExport(boolean superAdmin, Set<String> permissions) {
        return superAdmin || has(permissions, PERM_REPORT_EXPORT);
    }

    /** 报表数据范围：财务看全量，经理只看本部门 */
    public static boolean canViewAllDepartments(Set<String> roleKeys) {
        return isFinanceRole(roleKeys);
    }

    private static boolean has(Set<String> permissions, String perm) {
        return permissions != null && permissions.contains(perm);
    }
}

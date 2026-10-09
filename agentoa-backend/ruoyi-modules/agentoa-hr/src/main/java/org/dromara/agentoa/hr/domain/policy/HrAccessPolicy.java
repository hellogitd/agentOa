package org.dromara.agentoa.hr.domain.policy;

import java.util.Set;

/**
 * 模块 1 权限矩阵的纯函数实现，供服务层与测试共用。
 * <p>
 * 列表级数据范围（HR 全量、部门经理本部门及以下、员工仅本人）由角色 data_scope 与
 * MyBatis 数据权限拦截器完成；本类负责单条记录的越权判定。
 */
public final class HrAccessPolicy {

    public static final String PERM_VIEW_SENSITIVE = "hr:employee:sensitive";

    private HrAccessPolicy() {
    }

    /** 查看档案：本人、HR、管理员、同部门部门经理或直属主管。 */
    public static boolean canView(boolean superAdmin, boolean hr, Long actorUserId, Long actorDeptId,
                                  Long targetUserId, Long targetDeptId, Long targetLeaderUserId) {
        if (superAdmin || hr) {
            return true;
        }
        if (actorUserId != null && actorUserId.equals(targetUserId)) {
            return true;
        }
        if (actorUserId != null && actorUserId.equals(targetLeaderUserId)) {
            return true;
        }
        return actorDeptId != null && actorDeptId.equals(targetDeptId);
    }

    /** 编辑档案（HR 字段）：仅 HR 与管理员。经理只能申请（流程模块），员工仅自助字段。 */
    public static boolean canEdit(boolean superAdmin, boolean hr) {
        return superAdmin || hr;
    }

    /** 自助字段编辑：本人（HR 与管理员同样允许）。 */
    public static boolean canEditSelfService(boolean superAdmin, boolean hr, Long actorUserId, Long targetUserId) {
        return superAdmin || hr || (actorUserId != null && actorUserId.equals(targetUserId));
    }

    /** 入职、转正、离职命令：仅 HR 与管理员。 */
    public static boolean canManageLifecycle(boolean superAdmin, boolean hr) {
        return superAdmin || hr;
    }

    /** 导入导出：仅 HR 与管理员。 */
    public static boolean canImportExport(boolean superAdmin, boolean hr) {
        return superAdmin || hr;
    }

    /** 身份证、联系方式明文：管理员或持有 hr:employee:sensitive 授权的调用方。 */
    public static boolean canViewSensitive(boolean superAdmin, Set<String> permissions) {
        return superAdmin || (permissions != null && permissions.contains(PERM_VIEW_SENSITIVE));
    }

    /** 发起员工异动（调岗调薪）：管理员或持有 hr:change:add 授权的调用方。 */
    public static boolean canManageChange(boolean superAdmin, Set<String> permissions) {
        return superAdmin || (permissions != null && permissions.contains("hr:change:add"));
    }

    /** 调薪金额明文：管理员或持有 hr:change:sensitive 授权的调用方。 */
    public static boolean canViewChangeSalary(boolean superAdmin, Set<String> permissions) {
        return superAdmin || (permissions != null && permissions.contains("hr:change:sensitive"));
    }

    /** 维护合同、教育与工作经历：HR（hr:employee:edit）与管理员。 */
    public static boolean canManageHrRecords(boolean superAdmin, Set<String> permissions) {
        return superAdmin || (permissions != null && permissions.contains("hr:employee:edit"));
    }
}

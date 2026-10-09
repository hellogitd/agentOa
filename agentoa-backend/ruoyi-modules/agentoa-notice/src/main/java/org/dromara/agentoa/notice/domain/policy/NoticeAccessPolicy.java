package org.dromara.agentoa.notice.domain.policy;

import java.util.Set;

/**
 * 模块 5 权限矩阵的纯函数实现（docs/15 权限与业务规则）。
 * <p>
 * 公告管理动作按 nt:notice:* 授权；普通员工只能看到受众包含自己的已发布公告；
 * 消息事件查询与人工重投单独授权（nt:outbox:*）。
 */
public final class NoticeAccessPolicy {

    public static final String PERM_NOTICE_ADD = "nt:notice:add";
    public static final String PERM_NOTICE_EDIT = "nt:notice:edit";
    public static final String PERM_NOTICE_RECALL = "nt:notice:recall";
    public static final String PERM_NOTICE_REMOVE = "nt:notice:remove";
    public static final String PERM_NOTICE_READ = "nt:notice:read";
    public static final String PERM_OUTBOX_LIST = "nt:outbox:list";
    public static final String PERM_OUTBOX_REDELIVER = "nt:outbox:redeliver";

    private NoticeAccessPolicy() {
    }

    /** 公告管理者（发布/修改/撤回/删除/已读统计） */
    public static boolean canManage(boolean superAdmin, Set<String> permissions, String perm) {
        return superAdmin || has(permissions, perm);
    }

    /** 公告可见性：管理者/发布人可见全部，其他人仅受众快照内的已发布公告 */
    public static boolean canViewNotice(boolean superAdmin, Set<String> permissions,
                                        Long actorUserId, Long publisherId, boolean inAudience, boolean published) {
        if (superAdmin || has(permissions, PERM_NOTICE_ADD)) {
            return true;
        }
        if (actorUserId != null && actorUserId.equals(publisherId)) {
            return true;
        }
        return published && inAudience;
    }

    /** 已读统计：管理者或发布人 */
    public static boolean canReadStatus(boolean superAdmin, Set<String> permissions,
                                        Long actorUserId, Long publisherId) {
        return superAdmin || has(permissions, PERM_NOTICE_READ)
            || (actorUserId != null && actorUserId.equals(publisherId));
    }

    private static boolean has(Set<String> permissions, String perm) {
        return permissions != null && permissions.contains(perm);
    }
}

package org.dromara.agentoa.collaboration.domain.policy;

import org.dromara.agentoa.collaboration.domain.enums.Visibility;

import java.util.Set;

/**
 * 模块 7 对象级权限纯函数（docs/17 测试与退出条件）：
 * 私有日程越权、任务状态非法跳转、已完成任务修改、预约取消/签到归属是主要断言点。
 */
public final class CollaborationAccessPolicy {

    public static final String PERM_EVENT_ADD = "cl:event:add";
    public static final String PERM_EVENT_EDIT = "cl:event:edit";
    public static final String PERM_EVENT_REMOVE = "cl:event:remove";
    public static final String PERM_ROOM_ADD = "cl:room:add";
    public static final String PERM_ROOM_EDIT = "cl:room:edit";
    public static final String PERM_ROOM_REMOVE = "cl:room:remove";
    public static final String PERM_ROOM_BOOK = "cl:room:book";
    public static final String PERM_TASK_ADD = "cl:task:add";
    public static final String PERM_TASK_EDIT = "cl:task:edit";
    public static final String PERM_TASK_REMOVE = "cl:task:remove";

    private CollaborationAccessPolicy() {
    }

    /**
     * 日程可见性（docs/05 1.7：日程限组织者/参与人）：组织者与参与人始终可见；
     * 私有仅组织者与参与人；部门范围含组织者部门；全员对登录用户可见；超管可见全部。
     */
    public static boolean canViewEvent(Long actorUserId, Long organizerId, Set<Long> attendeeIds,
                                       Visibility visibility, Long actorDeptId, Long organizerDeptId,
                                       boolean superAdmin) {
        if (actorUserId == null) {
            return false;
        }
        if (superAdmin) {
            return true;
        }
        if (actorUserId.equals(organizerId)) {
            return true;
        }
        if (attendeeIds != null && attendeeIds.contains(actorUserId)) {
            return true;
        }
        return switch (visibility) {
            case PRIVATE -> false;
            case ATTENDEES -> false;
            case DEPT -> actorDeptId != null && actorDeptId.equals(organizerDeptId);
            case ALL -> true;
        };
    }

    /** 日程管理（修改/删除/取消）：仅组织者或超管 */
    public static boolean canManageEvent(Long actorUserId, Long organizerId, boolean superAdmin) {
        return actorUserId != null && (actorUserId.equals(organizerId) || superAdmin);
    }

    /** 任务可见性：指派人、负责人、协作者 */
    public static boolean canViewTask(Long actorUserId, Long assignerId, Long assigneeId, Set<Long> memberIds) {
        return actorUserId != null && (actorUserId.equals(assignerId) || actorUserId.equals(assigneeId)
            || (memberIds != null && memberIds.contains(actorUserId)));
    }

    /** 任务管理（修改/删除）：指派人或负责人（终态修改由服务层拒绝） */
    public static boolean canManageTask(Long actorUserId, Long assignerId, Long assigneeId) {
        return actorUserId != null && (actorUserId.equals(assignerId) || actorUserId.equals(assigneeId));
    }

    /** 预约取消/签到：预订人、关联日程组织者或超管 */
    public static boolean canManageBooking(Long actorUserId, Long bookerId, Long eventOrganizerId,
                                           boolean superAdmin) {
        return actorUserId != null && (actorUserId.equals(bookerId) || actorUserId.equals(eventOrganizerId)
            || superAdmin);
    }

    private static boolean has(Set<String> permissions, String perm) {
        return permissions != null && permissions.contains(perm);
    }

    /** 会议室维护权限 */
    public static boolean canManageRoom(boolean superAdmin, Set<String> permissions, String perm) {
        return superAdmin || has(permissions, perm);
    }
}

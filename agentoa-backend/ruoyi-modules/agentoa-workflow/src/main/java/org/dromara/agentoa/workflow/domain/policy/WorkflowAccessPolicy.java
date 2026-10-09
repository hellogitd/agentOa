package org.dromara.agentoa.workflow.domain.policy;

/**
 * 流程对象级授权（API 规范 1.7）：发起人、实际参与人与明确管理范围可见；办理需引擎当前办理人核验。
 */
public final class WorkflowAccessPolicy {

    private WorkflowAccessPolicy() {
    }

    public static boolean canViewInstance(boolean superAdmin, boolean monitorScope, Long viewerId, Long initiatorId, boolean participant) {
        if (superAdmin || monitorScope) {
            return true;
        }
        if (viewerId == null) {
            return false;
        }
        return participant || viewerId.equals(initiatorId);
    }

    /** 办理必须是引擎当前办理人或候选人；管理员监控不能默认代办（docs/12） */
    public static boolean canActOnTask(boolean assigneeOrCandidate) {
        return assigneeOrCandidate;
    }

    public static boolean canRevoke(boolean superAdmin, Long viewerId, Long initiatorId, boolean anyActionDone, boolean running) {
        if (!running || anyActionDone) {
            return false;
        }
        return superAdmin || (viewerId != null && viewerId.equals(initiatorId));
    }
}

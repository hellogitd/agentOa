package org.dromara.agentoa.workflow.assigner;

import org.dromara.common.core.utils.SpringUtils;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;

/**
 * 固定任务监听器（只出现在代码仓库模板中）：按受控规则解析办理人。
 * 多实例模式（WF-08）：子任务从 flowable:elementVariable=assignee 取本人办理人，
 * 兜底校验集合非空（解析失败整体回滚 400）。
 * 发起人自选（USER_SELECT）：从流程变量 {@code userSelect_<nodeId>} 取发起人选择结果，
 * 缺失确定性报错，不回退任意人。
 */
public class AssigneeTaskListener implements TaskListener {

    public static final String ELEMENT_VARIABLE = "assignee";
    public static final String MI_MARKER = "miMode";

    /** 由 Flowable 字段注入：SELF / LEADER / DEPT_HEAD / ROLE:key / WHITELIST:id,id / USER_SELECT */
    private Expression rule;

    @Override
    public void notify(DelegateTask delegateTask) {
        String ruleText = rule == null ? null : String.valueOf(rule.getValue(null));
        if (isMultiInstance(delegateTask)) {
            Object elementAssignee = delegateTask.getVariable(ELEMENT_VARIABLE);
            if (elementAssignee == null || String.valueOf(elementAssignee).isBlank()) {
                throw new AssigneeResolutionException("多实例集合为空，无法生成办理人任务");
            }
            delegateTask.setAssignee(String.valueOf(elementAssignee).trim());
            delegateTask.setVariableLocal(MI_MARKER, "1");
            delegateTask.setVariableLocal("assigneeRule", ruleText);
            return;
        }
        AssigneeResolver resolver = SpringUtils.getBean(AssigneeResolver.class);
        Long initiatorUserId = toLong(delegateTask.getVariable("initiatorUserId"));
        Long initiatorDeptId = toLong(delegateTask.getVariable("initiatorDeptId"));
        Object userSelection = delegateTask.getVariable(
            AssigneeResolver.USER_SELECT_VARIABLE_PREFIX + delegateTask.getTaskDefinitionKey());
        AssigneeResolver.AssigneeSet set = resolver.resolve(ruleText, initiatorUserId, initiatorDeptId, userSelection);
        if (set.assigneeUserId() != null) {
            delegateTask.setAssignee(String.valueOf(set.assigneeUserId()));
        } else {
            for (Long userId : set.candidateUserIds()) {
                delegateTask.addCandidateUser(String.valueOf(userId));
            }
        }
        delegateTask.setVariableLocal("assigneeRule", ruleText);
    }

    private boolean isMultiInstance(DelegateTask delegateTask) {
        return delegateTask.getVariable("nrOfInstances") != null
            || delegateTask.getVariable("nrOfCompletedInstances") != null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.valueOf(text);
    }
}

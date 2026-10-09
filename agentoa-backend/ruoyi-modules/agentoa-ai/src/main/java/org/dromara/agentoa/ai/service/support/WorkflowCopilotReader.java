package org.dromara.agentoa.ai.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.enums.AiCopilotScene;
import org.dromara.agentoa.workflow.domain.OaFlowInstance;
import org.dromara.agentoa.workflow.domain.OaFlowTaskAction;
import org.dromara.agentoa.workflow.domain.policy.WorkflowAccessPolicy;
import org.dromara.agentoa.workflow.mapper.OaFlowInstanceMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowTaskActionMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * 审批摘要只读上下文（docs/21 AI-M4-01）。
 * <p>
 * 只取表单关键字段、历史意见与耗时摘要；越权按 404 处理，不把明细泄给未参与人。
 */
@Component
@RequiredArgsConstructor
public class WorkflowCopilotReader implements CopilotBizReader {

    private final OaFlowInstanceMapper instanceMapper;
    private final OaFlowTaskActionMapper taskActionMapper;

    @Override
    public String scene() {
        return AiCopilotScene.APPROVE_SUMMARY.code();
    }

    @Override
    public String readContext(Long bizId, Long userId) {
        if (bizId == null) {
            return "";
        }
        OaFlowInstance instance = instanceMapper.selectById(bizId);
        if (instance == null) {
            throw new ServiceException("AI_COPILOT_BIZ_NOT_FOUND 审批单不存在或对当前用户不可见", 404);
        }
        boolean participant = instance.getInitiatorUserId() != null
            && instance.getInitiatorUserId().equals(userId)
            || taskActionMapper.selectCount(new LambdaQueryWrapper<OaFlowTaskAction>()
            .eq(OaFlowTaskAction::getInstanceId, bizId)
            .eq(OaFlowTaskAction::getOperatorUserId, userId)) > 0;
        if (!WorkflowAccessPolicy.canViewInstance(LoginHelper.isSuperAdmin(), false, userId,
            instance.getInitiatorUserId(), participant)) {
            throw new ServiceException("AI_COPILOT_BIZ_FORBIDDEN 无权查看该审批单", 403);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("审批标题：").append(nullToEmpty(instance.getTitle())).append('\n');
        sb.append("发起人：").append(nullToEmpty(instance.getInitiatorName())).append('\n');
        sb.append("当前节点：").append(nullToEmpty(instance.getCurrentTaskName())).append('\n');
        sb.append("状态：").append(statusLabel(instance.getStatus())).append('\n');
        if (instance.getStartTime() != null) {
            sb.append("发起时间：").append(format(instance.getStartTime())).append('\n');
        }
        if (instance.getEndTime() != null) {
            sb.append("结束时间：").append(format(instance.getEndTime())).append('\n');
        }
        if (instance.getDuration() != null && instance.getDuration() > 0) {
            sb.append("耗时：").append(instance.getDuration() / 60000).append(" 分钟\n");
        }
        sb.append("表单内容：").append(summarizeForm(instance.getFormData())).append('\n');
        List<OaFlowTaskAction> actions = taskActionMapper.selectList(new LambdaQueryWrapper<OaFlowTaskAction>()
            .eq(OaFlowTaskAction::getInstanceId, bizId)
            .orderByAsc(OaFlowTaskAction::getActionTime));
        if (!actions.isEmpty()) {
            sb.append("历史审批意见：\n");
            for (OaFlowTaskAction action : actions) {
                sb.append("- ").append(nullToEmpty(action.getTaskName()))
                    .append(" / ").append(actionLabel(action.getAction()))
                    .append(" / ").append(nullToEmpty(action.getOperatorName()))
                    .append("：").append(nullToEmpty(action.getComment())).append('\n');
            }
        }
        return sb.toString();
    }

    /** 表单 JSON 只做长度受限的原文摘要，避免注入超长上下文 */
    private String summarizeForm(String formData) {
        if (formData == null || formData.isBlank()) {
            return "（空）";
        }
        return formData.length() <= 2000 ? formData : formData.substring(0, 2000) + "…";
    }

    private String statusLabel(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 1 -> "审批中";
            case 2 -> "已通过";
            case 3 -> "已拒绝";
            case 4 -> "已撤销";
            case 5 -> "已挂起";
            case 6 -> "已终止";
            default -> "状态" + status;
        };
    }

    private String actionLabel(String action) {
        if (action == null) {
            return "处理";
        }
        return switch (action) {
            case "agree" -> "同意";
            case "reject" -> "驳回";
            case "transfer" -> "转办";
            case "cancel" -> "撤销";
            default -> action;
        };
    }

    private static String format(java.util.Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(date);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

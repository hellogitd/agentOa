package org.dromara.agentoa.ai.service.support.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.ai.service.support.AgentTool;
import org.dromara.agentoa.workflow.domain.OaFlowInstance;
import org.dromara.agentoa.workflow.mapper.OaFlowInstanceMapper;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * 报销进度查询工具（docs/21 AI-M5-01）：只返回当前用户本人发起的报销单进度，不越权。
 */
@Component
public class ReimburseProgressTool implements AgentTool {

    private static final int MAX_ROWS = 10;

    private final OaFlowInstanceMapper instanceMapper;

    public ReimburseProgressTool(OaFlowInstanceMapper instanceMapper) {
        this.instanceMapper = instanceMapper;
    }

    @Override
    public String code() {
        return "reimburse_progress";
    }

    @Override
    public String name() {
        return "报销进度查询";
    }

    @Override
    public String description() {
        return "查询当前用户本人报销单的审批进度";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\",\"description\":\"标题关键词\"}}}";
    }

    @Override
    public String execute(String argumentsJson, Long userId, String username) {
        String keyword = parse(argumentsJson).getStr("keyword");
        LambdaQueryWrapper<OaFlowInstance> wrapper = new LambdaQueryWrapper<OaFlowInstance>()
            .eq(OaFlowInstance::getInitiatorUserId, userId)
            .eq(OaFlowInstance::getBusinessType, "reimburse")
            .orderByDesc(OaFlowInstance::getId)
            .last("LIMIT " + MAX_ROWS);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(OaFlowInstance::getTitle, keyword.trim());
        }
        List<OaFlowInstance> instances = instanceMapper.selectList(wrapper);
        if (instances.isEmpty()) {
            return "没有查到本人的报销单";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();
        for (OaFlowInstance instance : instances) {
            sb.append("- ").append(instance.getTitle() == null ? "（无标题）" : instance.getTitle())
                .append("：").append(statusLabel(instance.getStatus()))
                .append(instance.getCurrentTaskName() == null ? "" : "，当前节点 " + instance.getCurrentTaskName())
                .append(instance.getStartTime() == null ? "" : "，发起 " + format.format(instance.getStartTime()))
                .append('\n');
        }
        return sb.toString();
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

    private JSONObject parse(String json) {
        if (json == null || json.isBlank()) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (RuntimeException e) {
            return new JSONObject();
        }
    }
}

package org.dromara.agentoa.ai.service.support.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.ai.service.support.AgentTool;
import org.dromara.agentoa.collaboration.domain.OaTask;
import org.dromara.agentoa.collaboration.mapper.OaTaskMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 待办查询工具（docs/21 AI-M5-01）：只返回当前用户自己的待办，不越权。
 */
@Component
public class TodoQueryTool implements AgentTool {

    private final OaTaskMapper taskMapper;

    public TodoQueryTool(OaTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public String code() {
        return "todo_query";
    }

    @Override
    public String name() {
        return "待办查询";
    }

    @Override
    public String description() {
        return "查询当前用户的待办任务列表";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\",\"description\":\"标题关键词\"}}}";
    }

    @Override
    public String execute(String argumentsJson, Long userId, String username) {
        String keyword = parse(argumentsJson).getStr("keyword");
        LambdaQueryWrapper<OaTask> wrapper = new LambdaQueryWrapper<OaTask>()
            .eq(OaTask::getAssigneeId, userId)
            .eq(OaTask::getDelFlag, 0)
            .in(OaTask::getStatus, 1, 2, 3)
            .orderByAsc(OaTask::getDueDate)
            .last("LIMIT 20");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(OaTask::getTitle, keyword.trim());
        }
        List<OaTask> tasks = taskMapper.selectList(wrapper);
        if (tasks.isEmpty()) {
            return "当前没有待办任务";
        }
        StringBuilder sb = new StringBuilder();
        for (OaTask task : tasks) {
            sb.append("- ").append(task.getTitle() == null ? "" : task.getTitle())
                .append("（").append(statusLabel(task.getStatus()))
                .append(task.getDueDate() == null ? "" : "，截止 " + task.getDueDate())
                .append("）\n");
        }
        return sb.toString();
    }

    private String statusLabel(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 1 -> "待办";
            case 2 -> "进行中";
            case 3 -> "已阻塞";
            case 4 -> "已完成";
            case 5 -> "已取消";
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

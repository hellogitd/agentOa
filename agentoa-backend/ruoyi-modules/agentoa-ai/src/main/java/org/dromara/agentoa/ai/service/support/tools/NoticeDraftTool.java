package org.dromara.agentoa.ai.service.support.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.dromara.agentoa.ai.service.support.AgentTool;
import org.springframework.stereotype.Component;

/**
 * 通知草稿工具（docs/21 AI-M5-01）：只产出草稿文本，不落业务表、不发布。
 */
@Component
public class NoticeDraftTool implements AgentTool {

    @Override
    public String code() {
        return "notice_draft";
    }

    @Override
    public String name() {
        return "通知草稿";
    }

    @Override
    public String description() {
        return "生成通知草稿文本（不发布）";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
            + "\"title\":{\"type\":\"string\",\"description\":\"通知标题\"},"
            + "\"points\":{\"type\":\"string\",\"description\":\"要点，分号分隔\"}},"
            + "\"required\":[\"title\"]}";
    }

    @Override
    public String execute(String argumentsJson, Long userId, String username) {
        JSONObject args = parse(argumentsJson);
        String title = args.getStr("title");
        if (title == null || title.isBlank()) {
            return "缺少 title 参数";
        }
        String points = args.getStr("points");
        StringBuilder sb = new StringBuilder();
        sb.append("通知草稿（AI 生成内容仅供参考，请人工核对后使用）\n\n");
        sb.append("标题：").append(title.trim()).append('\n');
        sb.append("正文：\n");
        if (points == null || points.isBlank()) {
            sb.append("（请补充事项要点）\n");
        } else {
            for (String point : points.split("[;；]")) {
                if (!point.isBlank()) {
                    sb.append("一、").append(point.trim()).append('\n');
                }
            }
        }
        sb.append("\n落款：（部门 / 日期）");
        return sb.toString();
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

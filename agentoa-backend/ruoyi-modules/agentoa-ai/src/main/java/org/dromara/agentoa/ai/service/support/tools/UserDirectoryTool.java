package org.dromara.agentoa.ai.service.support.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.dromara.agentoa.ai.mapper.AiIdentityMapper;
import org.dromara.agentoa.ai.service.support.AgentTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 企业通讯录查询工具（docs/21 AI-M5-01）：仅返回账号与姓名，不返回敏感人事信息。
 */
@Component
public class UserDirectoryTool implements AgentTool {

    private final AiIdentityMapper identityMapper;

    public UserDirectoryTool(AiIdentityMapper identityMapper) {
        this.identityMapper = identityMapper;
    }

    @Override
    public String code() {
        return "user_directory";
    }

    @Override
    public String name() {
        return "通讯录查询";
    }

    @Override
    public String description() {
        return "按姓名关键词查询同事账号信息";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\",\"description\":\"姓名关键词\"}},\"required\":[\"keyword\"]}";
    }

    @Override
    public String execute(String argumentsJson, Long userId, String username) {
        String keyword = parse(argumentsJson).getStr("keyword");
        if (keyword == null || keyword.isBlank()) {
            return "缺少 keyword 参数";
        }
        List<Map<String, Object>> users = identityMapper.searchUserBrief(keyword.trim());
        if (users.isEmpty()) {
            return "未找到匹配的同事";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> user : users) {
            sb.append("- ").append(user.get("nickName"))
                .append("（账号 ").append(user.get("userName")).append("）\n");
        }
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

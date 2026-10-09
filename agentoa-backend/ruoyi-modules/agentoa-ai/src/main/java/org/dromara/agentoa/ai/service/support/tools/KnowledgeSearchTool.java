package org.dromara.agentoa.ai.service.support.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.dromara.agentoa.ai.domain.OaAiKb;
import org.dromara.agentoa.ai.domain.vo.AiKbSearchHitVo;
import org.dromara.agentoa.ai.service.IAiKbService;
import org.dromara.agentoa.ai.service.support.AgentTool;
import org.dromara.agentoa.ai.service.support.KbRetriever;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库检索工具（docs/21 AI-M5-01）：复用 M3 RAG 检索，权限过滤同知识问答。
 */
@Component
public class KnowledgeSearchTool implements AgentTool {

    private final IAiKbService kbService;
    private final KbRetriever retriever;

    public KnowledgeSearchTool(IAiKbService kbService, KbRetriever retriever) {
        this.kbService = kbService;
        this.retriever = retriever;
    }

    @Override
    public String code() {
        return "knowledge_search";
    }

    @Override
    public String name() {
        return "知识库检索";
    }

    @Override
    public String description() {
        return "按关键词在已授权知识库中检索片段，返回标题与摘录";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"description\":\"检索关键词\"}},\"required\":[\"query\"]}";
    }

    @Override
    public String execute(String argumentsJson, Long userId, String username) {
        JSONObject args = parse(argumentsJson);
        String query = args.getStr("query");
        if (query == null || query.isBlank()) {
            return "缺少 query 参数";
        }
        List<OaAiKb> kbs = kbService.visibleKbs(userId, null);
        if (kbs.isEmpty()) {
            return "没有可见的知识库";
        }
        List<AiKbSearchHitVo> hits = retriever.search(userId, username, kbs, query, 5);
        if (hits.isEmpty()) {
            return "未检索到相关内容";
        }
        StringBuilder sb = new StringBuilder();
        for (AiKbSearchHitVo hit : hits) {
            sb.append("- ").append(hit.getTitle() == null ? "（无标题）" : hit.getTitle())
                .append("：").append(hit.getSnippet() == null ? "" : hit.getSnippet()).append('\n');
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

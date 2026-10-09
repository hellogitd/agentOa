package org.dromara.agentoa.ai.service.support.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.ai.service.support.AgentTool;
import org.dromara.agentoa.notice.domain.OaAnnouncement;
import org.dromara.agentoa.notice.mapper.OaAnnouncementMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 公告搜索工具（docs/21 AI-M5-01）：只检索已发布公告标题与摘要，不返回草稿/撤回件。
 */
@Component
public class NoticeSearchTool implements AgentTool {

    private static final int MAX_ROWS = 10;
    private static final int SNIPPET_CHARS = 120;

    private final OaAnnouncementMapper announcementMapper;

    public NoticeSearchTool(OaAnnouncementMapper announcementMapper) {
        this.announcementMapper = announcementMapper;
    }

    @Override
    public String code() {
        return "notice_search";
    }

    @Override
    public String name() {
        return "公告搜索";
    }

    @Override
    public String description() {
        return "按关键词检索已发布的公司公告，返回标题与摘要";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\",\"description\":\"标题或内容关键词\"}},\"required\":[\"keyword\"]}";
    }

    @Override
    public String execute(String argumentsJson, Long userId, String username) {
        String keyword = parse(argumentsJson).getStr("keyword");
        if (keyword == null || keyword.isBlank()) {
            return "缺少 keyword 参数";
        }
        String like = keyword.trim();
        List<OaAnnouncement> notices = announcementMapper.selectList(new LambdaQueryWrapper<OaAnnouncement>()
            .eq(OaAnnouncement::getStatus, 2)
            .and(inner -> inner.like(OaAnnouncement::getTitle, like).or().like(OaAnnouncement::getContent, like))
            .orderByDesc(OaAnnouncement::getPublishTime)
            .last("LIMIT " + MAX_ROWS));
        if (notices.isEmpty()) {
            return "没有匹配的公告";
        }
        StringBuilder sb = new StringBuilder();
        for (OaAnnouncement notice : notices) {
            sb.append("- ").append(notice.getTitle() == null ? "（无标题）" : notice.getTitle())
                .append("：").append(snippet(notice.getContent()))
                .append('\n');
        }
        return sb.toString();
    }

    private String snippet(String content) {
        if (content == null || content.isBlank()) {
            return "（无内容）";
        }
        String text = content.replaceAll("\\s+", " ").trim();
        return text.length() <= SNIPPET_CHARS ? text : text.substring(0, SNIPPET_CHARS) + "…";
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

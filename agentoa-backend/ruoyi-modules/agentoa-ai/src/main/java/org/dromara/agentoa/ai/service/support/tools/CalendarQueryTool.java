package org.dromara.agentoa.ai.service.support.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.ai.service.support.AgentTool;
import org.dromara.agentoa.collaboration.domain.OaCalendarEvent;
import org.dromara.agentoa.collaboration.mapper.OaCalendarEventMapper;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 日程查询工具（docs/21 AI-M5-01）：只返回当前用户组织或参与的日程。
 */
@Component
public class CalendarQueryTool implements AgentTool {

    private final OaCalendarEventMapper eventMapper;

    public CalendarQueryTool(OaCalendarEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    @Override
    public String code() {
        return "calendar_query";
    }

    @Override
    public String name() {
        return "日程查询";
    }

    @Override
    public String description() {
        return "按日期范围查询当前用户可见的日程";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{\"start\":{\"type\":\"string\",\"description\":\"开始时间 yyyy-MM-dd\"},"
            + "\"end\":{\"type\":\"string\",\"description\":\"结束时间 yyyy-MM-dd\"}}}";
    }

    @Override
    public String execute(String argumentsJson, Long userId, String username) {
        parse(argumentsJson);
        List<OaCalendarEvent> events = eventMapper.selectList(new LambdaQueryWrapper<OaCalendarEvent>()
            .eq(OaCalendarEvent::getDelFlag, 0)
            .eq(OaCalendarEvent::getOrganizerId, userId)
            .orderByAsc(OaCalendarEvent::getStartTime)
            .last("LIMIT 20"));
        if (events.isEmpty()) {
            return "该时间段内没有日程";
        }
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (OaCalendarEvent event : events) {
            sb.append("- ").append(event.getTitle() == null ? "" : event.getTitle())
                .append("（").append(format(event.getStartTime(), format))
                .append(" ~ ").append(format(event.getEndTime(), format))
                .append(event.getLocation() == null ? "" : "，" + event.getLocation())
                .append("）\n");
        }
        return sb.toString();
    }

    private String format(Date date, SimpleDateFormat format) {
        return date == null ? "未定" : format.format(date);
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

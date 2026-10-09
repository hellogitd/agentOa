package org.dromara.agentoa.ai.service.support.tools;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.dromara.agentoa.ai.service.support.AgentTool;
import org.dromara.agentoa.collaboration.domain.OaCalendarEvent;
import org.dromara.agentoa.collaboration.mapper.OaCalendarEventMapper;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 创建日程工具（docs/21 AI-M5-01/05）：写类工具，需人工确认 + {@code ai:tool:write}。
 * <p>
 * 只为当前用户创建本人组织的日程，不代他人创建。
 */
@Component
public class CalendarCreateTool implements AgentTool {

    private final OaCalendarEventMapper eventMapper;

    public CalendarCreateTool(OaCalendarEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    @Override
    public String code() {
        return "calendar_create";
    }

    @Override
    public String name() {
        return "创建日程";
    }

    @Override
    public String description() {
        return "为当前用户创建日程（写类，需人工确认）";
    }

    @Override
    public String parametersJsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
            + "\"title\":{\"type\":\"string\",\"description\":\"日程标题\"},"
            + "\"startTime\":{\"type\":\"string\",\"description\":\"开始时间 yyyy-MM-dd HH:mm\"},"
            + "\"endTime\":{\"type\":\"string\",\"description\":\"结束时间 yyyy-MM-dd HH:mm\"},"
            + "\"location\":{\"type\":\"string\",\"description\":\"地点\"}},"
            + "\"required\":[\"title\",\"startTime\"]}";
    }

    @Override
    public boolean writeTool() {
        return true;
    }

    @Override
    public String execute(String argumentsJson, Long userId, String username) {
        JSONObject args = parse(argumentsJson);
        String title = args.getStr("title");
        if (title == null || title.isBlank()) {
            return "缺少 title 参数";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        Date start = parseDate(args.getStr("startTime"), format);
        if (start == null) {
            return "startTime 格式应为 yyyy-MM-dd HH:mm";
        }
        Date end = parseDate(args.getStr("endTime"), format);
        if (end == null || end.before(start)) {
            end = new Date(start.getTime() + 30 * 60 * 1000L);
        }
        OaCalendarEvent event = new OaCalendarEvent();
        event.setLockVersion(0);
        event.setTitle(title.trim());
        event.setEventType(2);
        event.setStartTime(start);
        event.setEndTime(end);
        event.setIsAllDay(0);
        event.setLocation(args.getStr("location"));
        event.setOrganizerId(userId);
        event.setVisibility(1);
        event.setIsException(0);
        event.setStatus(1);
        event.setCreateBy(userId);
        event.setCreateTime(new Date());
        event.setDelFlag(0);
        eventMapper.insert(event);
        return "已创建日程「" + title.trim() + "」（ID " + event.getId() + "），请人工核对后确认";
    }

    private Date parseDate(String value, SimpleDateFormat format) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return format.parse(value.trim());
        } catch (ParseException e) {
            return null;
        }
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

package org.dromara.agentoa.ai.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.enums.AiCopilotScene;
import org.dromara.agentoa.collaboration.domain.OaCalendarEvent;
import org.dromara.agentoa.collaboration.mapper.CalendarAttendeeMapper;
import org.dromara.agentoa.collaboration.mapper.OaCalendarEventMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * 会议纪要只读上下文（docs/21 AI-M4-05）。
 * <p>
 * 仅组织者/参与人可见会议主题、时间、地点与议程描述；产出为纪要草稿，
 * 人工确认后才写入日程备注（AI 不直接改业务数据）。
 */
@Component
@RequiredArgsConstructor
public class MeetingCopilotReader implements CopilotBizReader {

    private static final int MAX_DESC_CHARS = 8000;

    private final OaCalendarEventMapper calendarEventMapper;
    private final CalendarAttendeeMapper calendarAttendeeMapper;

    @Override
    public String scene() {
        return AiCopilotScene.MINUTES.code();
    }

    @Override
    public String readContext(Long bizId, Long userId) {
        if (bizId == null) {
            return "";
        }
        OaCalendarEvent event = calendarEventMapper.selectById(bizId);
        if (event == null || event.getDelFlag() != null && event.getDelFlag() == 1) {
            throw new ServiceException("AI_COPILOT_BIZ_NOT_FOUND 日程不存在或对当前用户不可见", 404);
        }
        boolean organizer = event.getOrganizerId() != null && event.getOrganizerId().equals(userId);
        boolean attendee = calendarAttendeeMapper.exists(bizId, userId) > 0;
        if (!organizer && !attendee && !LoginHelper.isSuperAdmin()) {
            throw new ServiceException("AI_COPILOT_BIZ_FORBIDDEN 无权查看该日程", 403);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("会议主题：").append(nullToEmpty(event.getTitle())).append('\n');
        sb.append("类型：").append(event.getEventType() != null && event.getEventType() == 2 ? "会议" : "日程").append('\n');
        sb.append("时间：").append(format(event.getStartTime()))
            .append(" ~ ").append(format(event.getEndTime())).append('\n');
        sb.append("地点：").append(nullToEmpty(event.getLocation())).append('\n');
        List<Long> attendeeIds = calendarAttendeeMapper.selectUserIds(bizId);
        sb.append("参与人数：").append(attendeeIds == null ? 0 : attendeeIds.size()).append('\n');
        String description = event.getDescription();
        if (description != null && description.length() > MAX_DESC_CHARS) {
            description = description.substring(0, MAX_DESC_CHARS) + "…";
        }
        sb.append("议程/记录：").append(description == null ? "（空）" : description);
        return sb.toString();
    }

    private static String format(java.util.Date date) {
        return date == null ? "未定" : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(date);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

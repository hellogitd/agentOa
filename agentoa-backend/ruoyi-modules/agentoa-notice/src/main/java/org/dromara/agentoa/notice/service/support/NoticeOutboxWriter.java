package org.dromara.agentoa.notice.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 公告站内信 outbox 写入：业务事务内落事件，事务提交后由底座投递器转 nc_message 并推 WebSocket。
 * event_id 超长时折叠为确定性摘要，保证重复消费幂等。
 */
@Component
@RequiredArgsConstructor
public class NoticeOutboxWriter {

    public static final String EVENT_TYPE_NOTICE = "NOTICE";

    private final JdbcTemplate jdbc;

    public void writeNotice(String eventId, Long receiverUserId, String title, String content, Long noticeId) {
        write(eventId, receiverUserId, EVENT_TYPE_NOTICE, title, content, "notice", noticeId, "/notice/announcement");
    }

    /** 通用站内信事件（模板推送按模板 msg_type 落事件） */
    public void write(String eventId, Long receiverUserId, String eventType, String title, String content,
                      String bizType, Long bizId, String url) {
        String payload = "{\"title\":\"" + escape(title) + "\",\"content\":\"" + escape(content)
            + "\",\"bizType\":\"" + escape(bizType) + "\",\"bizId\":\"" + bizId + "\",\"url\":\"" + escape(url) + "\"}";
        jdbc.update("INSERT INTO sys_outbox(event_id, receiver_id, event_type, payload) VALUES(?,?,?,?)",
            compact(eventId), receiverUserId, eventType, payload);
    }

    /** sys_outbox.event_id 为 VARCHAR(64)，超长事件 ID 折叠为确定性摘要保持唯一键可重放 */
    static String compact(String eventId) {
        return eventId.length() <= 64 ? eventId : "E" + org.dromara.agentoa.hr.service.support.IdempotencyGuard
            .digest(eventId).substring(0, 63);
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

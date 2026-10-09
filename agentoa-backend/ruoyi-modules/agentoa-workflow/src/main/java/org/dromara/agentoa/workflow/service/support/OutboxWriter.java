package org.dromara.agentoa.workflow.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 事务 outbox 写入：与业务提交同事务（docs/03 section 4）。
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    public static final String EVENT_TYPE_TODO = "TODO";

    private final JdbcTemplate jdbc;

    public void writeTodo(String eventId, Long receiverUserId, String title, String content, String bizType, Long bizId, String url) {
        String payload = "{\"title\":\"" + escape(title) + "\",\"content\":\"" + escape(content)
            + "\",\"bizType\":\"" + escape(bizType) + "\",\"bizId\":\"" + bizId + "\",\"url\":\"" + escape(url) + "\"}";
        jdbc.update("INSERT INTO sys_outbox(event_id,receiver_id,event_type,payload) VALUES(?,?,?,?)",
            compact(eventId), receiverUserId, EVENT_TYPE_TODO, payload);
    }

    public void writeSystem(String eventId, Long receiverUserId, String title, String content) {
        String payload = "{\"title\":\"" + escape(title) + "\",\"content\":\"" + escape(content) + "\"}";
        jdbc.update("INSERT INTO sys_outbox(event_id,receiver_id,event_type,payload) VALUES(?,?,?,?)",
            compact(eventId), receiverUserId, "SYSTEM", payload);
    }

    /** sys_outbox.event_id 为 VARCHAR(64)：超长事件 ID 折叠为确定性摘要，保持唯一与可重放 */
    private String compact(String eventId) {
        return eventId.length() <= 64 ? eventId : "E" + FlowIdempotencyGuard.digest(eventId).substring(0, 63);
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

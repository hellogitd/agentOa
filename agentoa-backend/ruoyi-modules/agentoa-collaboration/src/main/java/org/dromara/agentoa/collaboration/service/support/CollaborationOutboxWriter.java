package org.dromara.agentoa.collaboration.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 协作通知 outbox 写入：业务事务内落事件，事务提交后由底座投递器落 nc_message 并推 WebSocket。
 * event_id 为业务动作的确定性键，重复消费不重复通知（docs/17 通知重复测试）。
 */
@Component
@RequiredArgsConstructor
public class CollaborationOutboxWriter {

    public static final String TYPE_TODO = "TODO";
    public static final String TYPE_NOTICE = "NOTICE";

    private final JdbcTemplate jdbc;

    /** 待办提醒（邀请、任务指派、状态变更） */
    public void writeTodo(String eventId, Long receiverUserId, String title, String content, String bizType, Long bizId) {
        write(eventId, receiverUserId, TYPE_TODO, title, content, bizType, bizId);
    }

    /** 通知（取消、成员变更） */
    public void writeNotice(String eventId, Long receiverUserId, String title, String content, String bizType, Long bizId) {
        write(eventId, receiverUserId, TYPE_NOTICE, title, content, bizType, bizId);
    }

    private void write(String eventId, Long receiverUserId, String type,
                       String title, String content, String bizType, Long bizId) {
        String payload = "{\"title\":\"" + escape(title) + "\",\"content\":\"" + escape(content)
            + "\",\"bizType\":\"" + escape(bizType) + "\",\"bizId\":\"" + bizId + "\",\"url\":\"" + url(bizType) + "\"}";
        jdbc.update("INSERT INTO sys_outbox(event_id, receiver_id, event_type, payload) VALUES(?,?,?,?)",
            compact(eventId), receiverUserId, type, payload);
    }

    private String url(String bizType) {
        return switch (bizType) {
            case "calendar" -> "/collaboration/event";
            case "booking" -> "/collaboration/room";
            default -> "/collaboration/task";
        };
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

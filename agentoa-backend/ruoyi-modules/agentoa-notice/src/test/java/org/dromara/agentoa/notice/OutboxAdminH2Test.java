package org.dromara.agentoa.notice;

import org.dromara.agentoa.notice.domain.bo.NoticePageQuery;
import org.dromara.agentoa.notice.support.NoticeTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * outbox 失败事件人工重投（docs/15 退出条件）：仅 FAILED 可重投，记录重投审计列。
 */
class OutboxAdminH2Test {

    @BeforeAll
    static void boot() {
        NoticeTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        NoticeTestEnvironment.clearData();
        NoticeTestEnvironment.logout();
        NoticeTestEnvironment.jdbcTemplate().update(
            "INSERT INTO sys_outbox(event_id, receiver_id, event_type, payload, status, retry_count, last_error) "
                + "VALUES('EVT-1', 200, 'NOTICE', '{\"title\":\"失败事件\"}', 'FAILED', 5, 'boom')");
        NoticeTestEnvironment.jdbcTemplate().update(
            "INSERT INTO sys_outbox(event_id, receiver_id, event_type, payload, status) "
                + "VALUES('EVT-2', 200, 'NOTICE', '{\"title\":\"正常事件\"}', 'DONE')");
    }

    @Test
    void listsFailedEventsWithTitle() {
        var page = NoticeTestEnvironment.outboxAdminService.list("FAILED", new NoticePageQuery());
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getEventId()).isEqualTo("EVT-1");
        assertThat(page.getRecords().get(0).getTitle()).isEqualTo("失败事件");
        assertThat(page.getRecords().get(0).getRetryCount()).isEqualTo(5);
    }

    @Test
    void redeliverResetsBackoffAndRecordsAudit() {
        var page = NoticeTestEnvironment.outboxAdminService.list("FAILED", new NoticePageQuery());
        Long id = page.getRecords().get(0).getId();
        NoticeTestEnvironment.outboxAdminService.redeliver(id, NoticeTestEnvironment.USER_HR);

        var after = NoticeTestEnvironment.outboxAdminService.list("PENDING", new NoticePageQuery());
        assertThat(after.getTotal()).isEqualTo(1);
        assertThat(after.getRecords().get(0).getRedeliverCount()).isEqualTo(1);
        assertThat(after.getRecords().get(0).getRedeliveredBy()).isEqualTo(NoticeTestEnvironment.USER_HR);
    }

    @Test
    void nonFailedEventsCannotBeRedelivered() {
        var page = NoticeTestEnvironment.outboxAdminService.list(null, new NoticePageQuery());
        Long doneId = page.getRecords().stream()
            .filter(record -> "DONE".equals(record.getStatus()))
            .findFirst().orElseThrow().getId();
        assertThatThrownBy(() -> NoticeTestEnvironment.outboxAdminService.redeliver(doneId, NoticeTestEnvironment.USER_HR))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_OUTBOX_NOT_REDELIVERABLE");
    }
}

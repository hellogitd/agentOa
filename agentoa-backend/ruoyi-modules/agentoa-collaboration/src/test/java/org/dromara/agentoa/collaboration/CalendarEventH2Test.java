package org.dromara.agentoa.collaboration;

import org.dromara.agentoa.collaboration.domain.bo.EventBo;
import org.dromara.agentoa.collaboration.domain.bo.EventPageQuery;
import org.dromara.agentoa.collaboration.domain.enums.BookingStatus;
import org.dromara.agentoa.collaboration.support.CollaborationTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 日程可见范围、参与人邀请与会议室联动（docs/17）：私有日程越权、通知去重、锁版本冲突、预约释放。
 */
class CalendarEventH2Test {

    @BeforeAll
    static void boot() {
        CollaborationTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        CollaborationTestEnvironment.clearData();
        CollaborationTestEnvironment.logout();
        CollaborationTestEnvironment.seedDept(CollaborationTestEnvironment.DEPT_A, "部门A");
        CollaborationTestEnvironment.seedDept(CollaborationTestEnvironment.DEPT_B, "部门B");
        CollaborationTestEnvironment.seedUser(CollaborationTestEnvironment.USER_ORGANIZER,
            CollaborationTestEnvironment.DEPT_A, "组织者");
        CollaborationTestEnvironment.seedUser(CollaborationTestEnvironment.USER_ATTENDEE,
            CollaborationTestEnvironment.DEPT_A, "参与人");
        CollaborationTestEnvironment.seedUser(CollaborationTestEnvironment.USER_STRANGER,
            CollaborationTestEnvironment.DEPT_B, "陌生人");
        CollaborationTestEnvironment.loginAs(CollaborationTestEnvironment.USER_ORGANIZER,
            CollaborationTestEnvironment.DEPT_A, Set.of("cl:event:add", "cl:event:edit", "cl:event:remove"));
    }

    @Test
    void visibilityMatrixHidesPrivateEventsFromStrangers() {
        var priv = CollaborationTestEnvironment.eventService.create(
            bo("私有日程", "1", null), CollaborationTestEnvironment.USER_ORGANIZER);
        CollaborationTestEnvironment.eventService.create(bo("部门日程", "3", null), CollaborationTestEnvironment.USER_ORGANIZER);
        CollaborationTestEnvironment.eventService.create(bo("全员日程", "4", null), CollaborationTestEnvironment.USER_ORGANIZER);

        EventPageQuery query = new EventPageQuery();
        query.setStart("2026-10-01T00:00:00Z");
        query.setEnd("2026-10-31T00:00:00Z");
        var mine = CollaborationTestEnvironment.eventService.list(query, CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(mine.getRecords()).hasSize(3);

        CollaborationTestEnvironment.loginAs(CollaborationTestEnvironment.USER_ATTENDEE,
            CollaborationTestEnvironment.DEPT_A, Set.of());
        var attendeeView = CollaborationTestEnvironment.eventService.list(query, CollaborationTestEnvironment.USER_ATTENDEE);
        assertThat(attendeeView.getRecords()).extracting("title")
            .contains("部门日程", "全员日程").doesNotContain("私有日程");

        CollaborationTestEnvironment.loginAs(CollaborationTestEnvironment.USER_STRANGER,
            CollaborationTestEnvironment.DEPT_B, Set.of());
        var strangerView = CollaborationTestEnvironment.eventService.list(query, CollaborationTestEnvironment.USER_STRANGER);
        assertThat(strangerView.getRecords()).extracting("title").containsExactly("全员日程");

        assertThatThrownBy(() -> CollaborationTestEnvironment.eventService.detail(priv.getId(),
            CollaborationTestEnvironment.USER_STRANGER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("CL_EVENT_NOT_FOUND");
    }

    @Test
    void attendeeInviteNotifiesOnceAndRemovalKeepsHistory() {
        var event = CollaborationTestEnvironment.eventService.create(
            bo("邀请日程", "2", String.valueOf(CollaborationTestEnvironment.USER_ATTENDEE)),
            CollaborationTestEnvironment.USER_ORGANIZER);
        long afterCreate = CollaborationTestEnvironment.outboxCount();
        assertThat(afterCreate).isEqualTo(1);

        // 重复添加同一参与人不重复通知（docs/17 通知重复）
        var same = bo("邀请日程", "2", String.valueOf(CollaborationTestEnvironment.USER_ATTENDEE));
        same.setLockVersion(event.getLockVersion());
        CollaborationTestEnvironment.eventService.update(event.getId(), same,
            CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(CollaborationTestEnvironment.outboxCount()).isEqualTo(afterCreate);

        // 移除保留记录（REMOVED）并通知
        var drop = bo("邀请日程", "2", null);
        drop.setLockVersion(event.getLockVersion() + 1);
        CollaborationTestEnvironment.eventService.update(event.getId(), drop,
            CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(CollaborationTestEnvironment.attendees.selectUserIds(event.getId()))
            .containsExactly(CollaborationTestEnvironment.USER_ATTENDEE);
        assertThat(CollaborationTestEnvironment.attendees
            .selectResponseStatus(event.getId(), CollaborationTestEnvironment.USER_ATTENDEE)).isEqualTo("REMOVED");
    }

    @Test
    void respondNotifiesOrganizerAndStrangerCannotRespond() {
        var event = CollaborationTestEnvironment.eventService.create(
            bo("响应日程", "2", String.valueOf(CollaborationTestEnvironment.USER_ATTENDEE)),
            CollaborationTestEnvironment.USER_ORGANIZER);
        var accepted = CollaborationTestEnvironment.eventService.respond(event.getId(), true,
            CollaborationTestEnvironment.USER_ATTENDEE);
        assertThat(accepted.getMyResponse()).isEqualTo("ACCEPTED");
        assertThat(CollaborationTestEnvironment.outboxCount()).isEqualTo(2);

        CollaborationTestEnvironment.loginAs(CollaborationTestEnvironment.USER_STRANGER,
            CollaborationTestEnvironment.DEPT_B, Set.of());
        assertThatThrownBy(() -> CollaborationTestEnvironment.eventService.respond(event.getId(), true,
            CollaborationTestEnvironment.USER_STRANGER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("CL_FORBIDDEN");
    }

    @Test
    void updateRejectsStaleLockVersion() {
        var event = CollaborationTestEnvironment.eventService.create(
            bo("锁版本日程", "2", null), CollaborationTestEnvironment.USER_ORGANIZER);
        var bo = bo("锁版本日程改", "2", null);
        bo.setLockVersion(event.getLockVersion());
        var updated = CollaborationTestEnvironment.eventService.update(event.getId(), bo,
            CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(updated.getLockVersion()).isEqualTo(event.getLockVersion() + 1);

        var stale = bo("过期修改", "2", null);
        stale.setLockVersion(event.getLockVersion());
        assertThatThrownBy(() -> CollaborationTestEnvironment.eventService.update(event.getId(), stale,
            CollaborationTestEnvironment.USER_ORGANIZER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("VERSION_CONFLICT");
    }

    @Test
    void eventWithRoomBooksSlotAndRejectsOverlap() {
        CollaborationTestEnvironment.seedRoom(1L, "一号会议室", 1);
        var first = CollaborationTestEnvironment.eventService.create(
            boWithRoom("占用会议", "2", "1"), CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(first.getRoomId()).isEqualTo(1L);
        Long linked = CollaborationTestEnvironment.jdbcTemplate()
            .queryForObject("SELECT COUNT(*) FROM oa_room_booking WHERE event_id = ?", Long.class, first.getId());
        assertThat(linked).isEqualTo(1);

        assertThatThrownBy(() -> CollaborationTestEnvironment.eventService.create(
            boWithRoom("重叠会议", "2", "1"), CollaborationTestEnvironment.USER_ORGANIZER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("CL_ROOM_CONFLICT");
    }

    @Test
    void deleteReleasesBookingButKeepsHistory() {
        CollaborationTestEnvironment.seedRoom(1L, "一号会议室", 1);
        var event = CollaborationTestEnvironment.eventService.create(
            boWithRoom("释放会议", "2", "1"), CollaborationTestEnvironment.USER_ORGANIZER);
        CollaborationTestEnvironment.eventService.delete(event.getId(), CollaborationTestEnvironment.USER_ORGANIZER);

        Integer status = CollaborationTestEnvironment.jdbcTemplate()
            .queryForObject("SELECT status FROM oa_room_booking WHERE event_id = ?", Integer.class, event.getId());
        assertThat(status).isEqualTo(BookingStatus.RELEASED.code());
        // 释放后同一时段可以再预约
        var replacement = CollaborationTestEnvironment.eventService.create(
            boWithRoom("再约会议", "2", "1"), CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(replacement.getRoomId()).isEqualTo(1L);
    }

    private static EventBo bo(String title, String visibility, String attendeeIds) {
        EventBo bo = new EventBo();
        bo.setTitle(title);
        bo.setDescription("日程描述");
        bo.setEventType("1");
        bo.setStartTime("2026-10-05T09:00:00Z");
        bo.setEndTime("2026-10-05T10:00:00Z");
        bo.setVisibility(visibility);
        bo.setAttendeeIds(attendeeIds == null ? List.of() : List.of(attendeeIds));
        return bo;
    }

    private static EventBo boWithRoom(String title, String visibility, String roomId) {
        EventBo bo = bo(title, visibility, null);
        bo.setEventType("2");
        bo.setRoomId(roomId);
        return bo;
    }
}

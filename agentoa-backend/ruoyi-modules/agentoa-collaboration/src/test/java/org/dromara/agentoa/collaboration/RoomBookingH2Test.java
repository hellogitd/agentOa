package org.dromara.agentoa.collaboration;

import org.dromara.agentoa.collaboration.domain.bo.BookingBo;
import org.dromara.agentoa.collaboration.domain.bo.CollabPageQuery;
import org.dromara.agentoa.collaboration.domain.enums.BookingStatus;
import org.dromara.agentoa.collaboration.support.CollaborationTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会议室预约（docs/17 第 2/4 步）：并发/重叠拒绝、Idempotency-Key 重放、取消释放但保留历史。
 */
class RoomBookingH2Test {

    @BeforeAll
    static void boot() {
        CollaborationTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        CollaborationTestEnvironment.clearData();
        CollaborationTestEnvironment.logout();
        CollaborationTestEnvironment.seedUser(CollaborationTestEnvironment.USER_ORGANIZER,
            CollaborationTestEnvironment.DEPT_A, "组织者");
        CollaborationTestEnvironment.seedUser(CollaborationTestEnvironment.USER_ATTENDEE,
            CollaborationTestEnvironment.DEPT_A, "参与人");
        CollaborationTestEnvironment.seedUser(CollaborationTestEnvironment.USER_STRANGER,
            CollaborationTestEnvironment.DEPT_B, "陌生人");
        CollaborationTestEnvironment.seedRoom(1L, "一号会议室", 1);
        CollaborationTestEnvironment.loginAs(CollaborationTestEnvironment.USER_ORGANIZER,
            CollaborationTestEnvironment.DEPT_A, Set.of("cl:room:book", "cl:room:edit"));
    }

    @Test
    void bookingIsIdempotentAndConflictsOnSameKeyDifferentBody() {
        var booking = CollaborationTestEnvironment.bookingService.book(1L,
            booking("需求评审", "2026-10-06T02:00:00Z", "2026-10-06T03:00:00Z"), "book-1",
            CollaborationTestEnvironment.USER_ORGANIZER);
        var replay = CollaborationTestEnvironment.bookingService.book(1L,
            booking("需求评审", "2026-10-06T02:00:00Z", "2026-10-06T03:00:00Z"), "book-1",
            CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(replay.getId()).isEqualTo(booking.getId());
        assertThat(CollaborationTestEnvironment.jdbcTemplate()
            .queryForObject("SELECT COUNT(*) FROM oa_room_booking", Long.class)).isEqualTo(1);

        assertThatThrownBy(() -> CollaborationTestEnvironment.bookingService.book(1L,
            booking("另一场会", "2026-10-06T04:00:00Z", "2026-10-06T05:00:00Z"), "book-1",
            CollaborationTestEnvironment.USER_ORGANIZER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void overlappingBookingRejectedEvenForOtherUsers() {
        CollaborationTestEnvironment.bookingService.book(1L,
            booking("上午会议", "2026-10-06T02:00:00Z", "2026-10-06T04:00:00Z"), "book-2",
            CollaborationTestEnvironment.USER_ORGANIZER);
        assertThatThrownBy(() -> CollaborationTestEnvironment.bookingService.book(1L,
            booking("重叠会议", "2026-10-06T03:00:00Z", "2026-10-06T05:00:00Z"), "book-3",
            CollaborationTestEnvironment.USER_ATTENDEE))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("CL_ROOM_CONFLICT");
        // 首尾相接（左闭右开）不视为冲突
        var backToBack = CollaborationTestEnvironment.bookingService.book(1L,
            booking("边界会议", "2026-10-06T04:00:00Z", "2026-10-06T05:00:00Z"), "book-4",
            CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(backToBack.getStatus()).isEqualTo(BookingStatus.BOOKED.code());
    }

    @Test
    void cancelFreesSlotButKeepsHistoryAndRejectsSecondCancel() {
        var booking = CollaborationTestEnvironment.bookingService.book(1L,
            booking("待取消", "2026-10-06T06:00:00Z", "2026-10-06T07:00:00Z"), "book-5",
            CollaborationTestEnvironment.USER_ORGANIZER);
        var cancelled = CollaborationTestEnvironment.bookingService.cancel(booking.getId(),
            CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED.code());

        // 取消竞态：第二次取消返回 409
        assertThatThrownBy(() -> CollaborationTestEnvironment.bookingService.cancel(booking.getId(),
            CollaborationTestEnvironment.USER_ORGANIZER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("CL_STATE_CONFLICT");
        // 历史保留
        assertThat(CollaborationTestEnvironment.jdbcTemplate()
            .queryForObject("SELECT COUNT(*) FROM oa_room_booking", Long.class)).isEqualTo(1);
        // 时段释放后可再预约
        var rebooked = CollaborationTestEnvironment.bookingService.book(1L,
            booking("再约", "2026-10-06T06:00:00Z", "2026-10-06T07:00:00Z"), "book-6",
            CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(rebooked.getStatus()).isEqualTo(BookingStatus.BOOKED.code());
    }

    @Test
    void checkinOnlyOnceByBooker() {
        var booking = CollaborationTestEnvironment.bookingService.book(1L,
            booking("签到会议", "2026-10-06T08:00:00Z", "2026-10-06T09:00:00Z"), "book-7",
            CollaborationTestEnvironment.USER_ORGANIZER);
        var checked = CollaborationTestEnvironment.bookingService.checkin(booking.getId(),
            CollaborationTestEnvironment.USER_ORGANIZER);
        assertThat(checked.getStatus()).isEqualTo(BookingStatus.CHECKED_IN.code());
        assertThat(checked.getCheckinTime()).isNotNull();
        assertThatThrownBy(() -> CollaborationTestEnvironment.bookingService.checkin(booking.getId(),
            CollaborationTestEnvironment.USER_ORGANIZER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("CL_STATE_CONFLICT");
        // 越权签到
        CollaborationTestEnvironment.loginAs(CollaborationTestEnvironment.USER_STRANGER,
            CollaborationTestEnvironment.DEPT_B, Set.of("cl:room:book"));
        assertThatThrownBy(() -> CollaborationTestEnvironment.bookingService.checkin(booking.getId(),
            CollaborationTestEnvironment.USER_STRANGER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("CL_FORBIDDEN");
    }

    @Test
    void maintenanceRoomRejectsBooking() {
        CollaborationTestEnvironment.seedRoom(2L, "维护中会议室", 2);
        assertThatThrownBy(() -> CollaborationTestEnvironment.bookingService.book(2L,
            booking("维护中", "2026-10-06T02:00:00Z", "2026-10-06T03:00:00Z"), "book-8",
            CollaborationTestEnvironment.USER_ORGANIZER))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("CL_ROOM_UNAVAILABLE");
    }

    @Test
    void listFiltersByRoomAndRange() {
        CollaborationTestEnvironment.bookingService.book(1L,
            booking("上午", "2026-10-06T02:00:00Z", "2026-10-06T03:00:00Z"), "book-9",
            CollaborationTestEnvironment.USER_ORGANIZER);
        var query = new CollabPageQuery();
        var page = CollaborationTestEnvironment.bookingService.list(1L, "2026-10-06T00:00:00Z",
            "2026-10-07T00:00:00Z", query);
        assertThat(page.getRecords()).hasSize(1);
        var outside = CollaborationTestEnvironment.bookingService.list(1L, "2026-10-07T00:00:00Z",
            "2026-10-08T00:00:00Z", query);
        assertThat(outside.getRecords()).isEmpty();
    }

    private static BookingBo booking(String title, String start, String end) {
        BookingBo bo = new BookingBo();
        bo.setTitle(title);
        bo.setStartTime(start);
        bo.setEndTime(end);
        return bo;
    }
}

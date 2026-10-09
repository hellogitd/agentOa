package org.dromara.agentoa.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.collaboration.domain.OaCalendarEvent;
import org.dromara.agentoa.collaboration.domain.OaMeetingRoom;
import org.dromara.agentoa.collaboration.domain.OaRoomBooking;
import org.dromara.agentoa.collaboration.domain.bo.BookingBo;
import org.dromara.agentoa.collaboration.domain.bo.CollabPageQuery;
import org.dromara.agentoa.collaboration.domain.enums.BookingStatus;
import org.dromara.agentoa.collaboration.domain.enums.EventStatus;
import org.dromara.agentoa.collaboration.domain.policy.CollaborationAccessPolicy;
import org.dromara.agentoa.collaboration.domain.vo.BookingVo;
import org.dromara.agentoa.collaboration.mapper.CollaborationIdentityReadMapper;
import org.dromara.agentoa.collaboration.mapper.OaCalendarEventMapper;
import org.dromara.agentoa.collaboration.mapper.OaMeetingRoomMapper;
import org.dromara.agentoa.collaboration.mapper.OaRoomBookingMapper;
import org.dromara.agentoa.collaboration.service.IRoomBookingService;
import org.dromara.agentoa.collaboration.service.support.CollaborationOutboxWriter;
import org.dromara.agentoa.collaboration.service.support.TimeUtil;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.hr.service.support.IdempotencyGuard;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 预约实现（docs/17 第 2/4 步）：重叠检查用行锁（selectOverlappingForUpdate）+ 状态条件更新，
 * 不依赖 Redis 锁；Idempotency-Key 重放返回首次结果（docs/05 1.7）；取消释放时段但保留历史。
 */
@Service
@RequiredArgsConstructor
public class RoomBookingServiceImpl implements IRoomBookingService {

    static final String BOOK_PATH = "POST /api/v1/calendar/rooms/{id}/bookings";

    private final OaRoomBookingMapper bookingMapper;
    private final OaMeetingRoomMapper roomMapper;
    private final OaCalendarEventMapper eventMapper;
    private final CollaborationIdentityReadMapper identityMapper;
    private final IdempotencyGuard idempotencyGuard;
    private final CollaborationOutboxWriter outboxWriter;

    @Override
    public PageVo<BookingVo> list(Long roomId, String start, String end, CollabPageQuery query) {
        LambdaQueryWrapper<OaRoomBooking> wrapper = new LambdaQueryWrapper<OaRoomBooking>();
        if (roomId != null) {
            wrapper.eq(OaRoomBooking::getRoomId, roomId);
        }
        if (start != null && !start.isBlank()) {
            wrapper.gt(OaRoomBooking::getEndTime, TimeUtil.parseInstant(start, "start"));
        }
        if (end != null && !end.isBlank()) {
            wrapper.lt(OaRoomBooking::getStartTime, TimeUtil.parseInstant(end, "end"));
        }
        wrapper.orderByAsc(OaRoomBooking::getStartTime);
        IPage<OaRoomBooking> result = bookingMapper.selectPage(
            new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        List<BookingVo> records = new ArrayList<>();
        for (OaRoomBooking booking : result.getRecords()) {
            records.add(toVo(booking));
        }
        return PageVo.of(records, result.getTotal(), query.safePageNum(), query.safePageSize());
    }

    @Override
    public BookingVo detail(Long id, Long actorUserId) {
        return toVo(require(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingVo book(Long roomId, BookingBo bo, String idempotencyKey, Long actorUserId) {
        String key = idempotencyKey == null ? null : idempotencyKey.trim();
        if (key == null || key.isEmpty()) {
            throw new ServiceException("缺少 Idempotency-Key 请求头", 400);
        }
        String replayRef = idempotencyGuard.begin(actorUserId, key,
            BOOK_PATH.replace("{id}", String.valueOf(roomId)),
            IdempotencyGuard.digest("book:" + roomId + ":" + bo.getEventId() + ":" + bo.getTitle()
                + ":" + bo.getStartTime() + ":" + bo.getEndTime()));
        if (replayRef != null) {
            return toVo(bookingMapper.selectById(Long.valueOf(replayRef)));
        }
        Date start = TimeUtil.parseInstant(bo.getStartTime(), "startTime");
        Date end = TimeUtil.parseInstant(bo.getEndTime(), "endTime");
        TimeUtil.validateRange(start, end);
        OaMeetingRoom room = roomMapper.selectById(roomId);
        if (room == null || room.getDelFlag() != null && room.getDelFlag() != 0) {
            throw new ServiceException("CL_ROOM_NOT_FOUND 会议室不存在", 404);
        }
        if (room.getStatus() == null || room.getStatus() != 1) {
            throw new ServiceException("CL_ROOM_UNAVAILABLE 会议室维护中不可预约", 409);
        }
        if (!bookingMapper.selectOverlappingForUpdate(roomId, start, end).isEmpty()) {
            throw new ServiceException("CL_ROOM_CONFLICT 会议室时段冲突", 409);
        }
        Date now = new Date();
        OaRoomBooking booking = new OaRoomBooking();
        booking.setLockVersion(0);
        booking.setRoomId(roomId);
        booking.setTitle(bo.getTitle().trim());
        booking.setBookerId(actorUserId);
        booking.setStartTime(start);
        booking.setEndTime(end);
        booking.setStatus(BookingStatus.BOOKED.code());
        booking.setCreateTime(now);
        booking.setUpdateTime(now);
        if (bo.getEventId() != null && !bo.getEventId().isBlank()) {
            Long eventId = Long.valueOf(bo.getEventId().trim());
            OaCalendarEvent event = eventMapper.selectById(eventId);
            if (event == null || event.getDelFlag() != null && event.getDelFlag() != 0) {
                throw new ServiceException("CL_EVENT_NOT_FOUND 日程不存在", 404);
            }
            if (!CollaborationAccessPolicy.canManageEvent(actorUserId, event.getOrganizerId(), isSuperAdmin())) {
                throw new ServiceException("CL_FORBIDDEN 仅组织者可为日程预约会议室", 403);
            }
            OaRoomBooking existing = bookingMapper.selectOne(new LambdaQueryWrapper<OaRoomBooking>()
                .eq(OaRoomBooking::getEventId, eventId));
            if (existing != null) {
                throw new ServiceException("CL_BOOKING_EXISTS 该日程已有预约", 409);
            }
            booking.setEventId(eventId);
        }
        bookingMapper.insert(booking);
        idempotencyGuard.complete(actorUserId, key, String.valueOf(booking.getId()));
        if (booking.getEventId() != null) {
            OaCalendarEvent event = eventMapper.selectById(booking.getEventId());
            if (event != null && !Objects.equals(event.getOrganizerId(), actorUserId)) {
                outboxWriter.writeNotice("CL-BOOK-" + booking.getId() + "-" + event.getOrganizerId(),
                    event.getOrganizerId(), "会议室预约",
                    identityMapper.selectNickName(actorUserId) + " 为日程「" + event.getTitle() + "」预约了会议室「"
                        + room.getName() + "」", "booking", booking.getId());
            }
        }
        return toVo(booking);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingVo checkin(Long id, Long actorUserId) {
        OaRoomBooking booking = require(id);
        requireManageable(booking, actorUserId);
        Date now = new Date();
        int updated = bookingMapper.update(null, new LambdaUpdateWrapper<OaRoomBooking>()
            .eq(OaRoomBooking::getId, id)
            .eq(OaRoomBooking::getStatus, BookingStatus.BOOKED.code())
            .set(OaRoomBooking::getStatus, BookingStatus.CHECKED_IN.code())
            .set(OaRoomBooking::getCheckinTime, now)
            .set(OaRoomBooking::getUpdateTime, now));
        if (updated == 0) {
            throw new ServiceException("CL_STATE_CONFLICT 预约状态不可签到", 409);
        }
        booking.setStatus(BookingStatus.CHECKED_IN.code());
        booking.setCheckinTime(now);
        return toVo(booking);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingVo cancel(Long id, Long actorUserId) {
        OaRoomBooking booking = require(id);
        requireManageable(booking, actorUserId);
        Date now = new Date();
        int updated = bookingMapper.update(null, new LambdaUpdateWrapper<OaRoomBooking>()
            .eq(OaRoomBooking::getId, id)
            .in(OaRoomBooking::getStatus, BookingStatus.BOOKED.code(), BookingStatus.CHECKED_IN.code())
            .set(OaRoomBooking::getStatus, BookingStatus.CANCELLED.code())
            .set(OaRoomBooking::getUpdateTime, now));
        if (updated == 0) {
            throw new ServiceException("CL_STATE_CONFLICT 预约已取消或已释放", 409);
        }
        booking.setStatus(BookingStatus.CANCELLED.code());
        if (booking.getEventId() != null) {
            OaCalendarEvent event = eventMapper.selectById(booking.getEventId());
            if (event != null) {
                eventMapper.update(null, new LambdaUpdateWrapper<OaCalendarEvent>()
                    .eq(OaCalendarEvent::getId, event.getId())
                    .set(OaCalendarEvent::getRoomId, null)
                    .set(OaCalendarEvent::getUpdateTime, now));
                if (!Objects.equals(event.getOrganizerId(), actorUserId)) {
                    outboxWriter.writeNotice("CL-BCAN-" + booking.getId() + "-" + event.getOrganizerId(),
                        event.getOrganizerId(), "会议室取消",
                        identityMapper.selectNickName(actorUserId) + " 取消了日程「" + event.getTitle()
                            + "」的会议室预约", "booking", booking.getId());
                }
            }
        }
        return toVo(booking);
    }

    // ------------------------------------------------------------ internal

    private OaRoomBooking require(Long id) {
        OaRoomBooking booking = bookingMapper.selectById(id);
        if (booking == null) {
            throw new ServiceException("CL_BOOKING_NOT_FOUND 预约不存在", 404);
        }
        return booking;
    }

    private void requireManageable(OaRoomBooking booking, Long actorUserId) {
        Long organizerId = booking.getEventId() == null ? null
            : eventMapper.selectById(booking.getEventId()) == null ? null
            : eventMapper.selectById(booking.getEventId()).getOrganizerId();
        if (!CollaborationAccessPolicy.canManageBooking(actorUserId, booking.getBookerId(), organizerId,
            isSuperAdmin())) {
            throw new ServiceException("CL_FORBIDDEN 仅预订人或组织者可操作该预约", 403);
        }
    }

    private boolean isSuperAdmin() {
        return org.dromara.common.satoken.utils.LoginHelper.isSuperAdmin();
    }

    private BookingVo toVo(OaRoomBooking booking) {
        BookingVo vo = new BookingVo();
        vo.setId(booking.getId());
        vo.setLockVersion(booking.getLockVersion());
        vo.setRoomId(booking.getRoomId());
        OaMeetingRoom room = roomMapper.selectById(booking.getRoomId());
        vo.setRoomName(room == null ? null : room.getName());
        vo.setEventId(booking.getEventId());
        vo.setTitle(booking.getTitle());
        vo.setBookerId(booking.getBookerId());
        vo.setBookerName(identityMapper.selectNickName(booking.getBookerId()));
        vo.setStartTime(TimeUtil.format(booking.getStartTime()));
        vo.setEndTime(TimeUtil.format(booking.getEndTime()));
        vo.setCheckinTime(TimeUtil.format(booking.getCheckinTime()));
        vo.setStatus(booking.getStatus());
        Long actorUserId = org.dromara.common.satoken.utils.LoginHelper.getUserId();
        vo.setCanManage(CollaborationAccessPolicy.canManageBooking(actorUserId, booking.getBookerId(),
            booking.getEventId() == null ? null : eventOrganizer(booking.getEventId()), isSuperAdmin()));
        vo.setCreateTime(TimeUtil.format(booking.getCreateTime()));
        return vo;
    }

    private Long eventOrganizer(Long eventId) {
        OaCalendarEvent event = eventMapper.selectById(eventId);
        return event == null ? null : event.getOrganizerId();
    }
}

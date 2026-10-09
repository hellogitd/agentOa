package org.dromara.agentoa.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.collaboration.domain.OaCalendarEvent;
import org.dromara.agentoa.collaboration.domain.OaMeetingRoom;
import org.dromara.agentoa.collaboration.domain.OaRoomBooking;
import org.dromara.agentoa.collaboration.domain.bo.EventBo;
import org.dromara.agentoa.collaboration.domain.bo.EventPageQuery;
import org.dromara.agentoa.collaboration.domain.enums.AttendeeResponse;
import org.dromara.agentoa.collaboration.domain.enums.BookingStatus;
import org.dromara.agentoa.collaboration.domain.enums.EventStatus;
import org.dromara.agentoa.collaboration.domain.enums.Visibility;
import org.dromara.agentoa.collaboration.domain.policy.CollaborationAccessPolicy;
import org.dromara.agentoa.collaboration.domain.vo.EventVo;
import org.dromara.agentoa.collaboration.mapper.CalendarAttendeeMapper;
import org.dromara.agentoa.collaboration.mapper.CollaborationIdentityReadMapper;
import org.dromara.agentoa.collaboration.mapper.OaCalendarEventMapper;
import org.dromara.agentoa.collaboration.mapper.OaMeetingRoomMapper;
import org.dromara.agentoa.collaboration.mapper.OaRoomBookingMapper;
import org.dromara.agentoa.collaboration.service.ICalendarEventService;
import org.dromara.agentoa.collaboration.service.support.CollaborationOutboxWriter;
import org.dromara.agentoa.collaboration.service.support.RecurrenceRule;
import org.dromara.agentoa.collaboration.service.support.TimeUtil;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 日程实现：时间存储 UTC；可见范围服务端过滤（私有日程越权拒绝）；
 * 参与人增删保留记录并通知；带会议室的日程在同一事务锁定时段并生成一一对应预约。
 */
@Service
@RequiredArgsConstructor
public class CalendarEventServiceImpl implements ICalendarEventService {

    private final OaCalendarEventMapper eventMapper;
    private final CalendarAttendeeMapper attendeeMapper;
    private final CollaborationIdentityReadMapper identityMapper;
    private final OaMeetingRoomMapper roomMapper;
    private final OaRoomBookingMapper bookingMapper;
    private final CollaborationOutboxWriter outboxWriter;

    @Override
    public PageVo<EventVo> list(EventPageQuery query, Long actorUserId) {
        Date start = TimeUtil.parseInstant(query.getStart(), "start");
        Date end = TimeUtil.parseInstant(query.getEnd(), "end");
        TimeUtil.validateRange(start, end);
        LambdaQueryWrapper<OaCalendarEvent> wrapper = new LambdaQueryWrapper<OaCalendarEvent>()
            .eq(OaCalendarEvent::getDelFlag, 0)
            .lt(OaCalendarEvent::getStartTime, end)
            .gt(OaCalendarEvent::getEndTime, start);
        if ("organized".equalsIgnoreCase(query.getScope())) {
            wrapper.eq(OaCalendarEvent::getOrganizerId, actorUserId);
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(OaCalendarEvent::getTitle, query.getKeyword().trim());
        }
        wrapper.orderByAsc(OaCalendarEvent::getStartTime);
        IPage<OaCalendarEvent> result = eventMapper.selectPage(
            new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        List<EventVo> records = new ArrayList<>();
        for (OaCalendarEvent event : result.getRecords()) {
            if (visible(event, actorUserId)) {
                records.add(toVo(event, actorUserId));
            }
        }
        return PageVo.of(records, result.getTotal(), query.safePageNum(), query.safePageSize());
    }

    @Override
    public EventVo detail(Long id, Long actorUserId) {
        OaCalendarEvent event = require(id);
        if (!visible(event, actorUserId)) {
            throw new ServiceException("CL_EVENT_NOT_FOUND 日程不存在或无权查看", 404);
        }
        return toVo(event, actorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventVo create(EventBo bo, Long actorUserId) {
        Date start = TimeUtil.parseInstant(bo.getStartTime(), "startTime");
        Date end = TimeUtil.parseInstant(bo.getEndTime(), "endTime");
        TimeUtil.validateRange(start, end);
        Date now = new Date();
        OaCalendarEvent event = new OaCalendarEvent();
        event.setLockVersion(0);
        event.setTitle(bo.getTitle().trim());
        event.setDescription(bo.getDescription());
        event.setEventType(Integer.valueOf(bo.getEventType() == null ? "1" : bo.getEventType()));
        event.setStartTime(start);
        event.setEndTime(end);
        event.setIsAllDay(bo.getIsAllDay() == null ? 0 : bo.getIsAllDay());
        event.setLocation(bo.getLocation());
        event.setOrganizerId(actorUserId);
        event.setVisibility(Visibility.from(bo.getVisibility() == null ? null : Integer.valueOf(bo.getVisibility())).code());
        event.setRemindMinutes(bo.getRemindMinutes() == null ? 15 : bo.getRemindMinutes());
        event.setStatus(EventStatus.NORMAL.code());
        event.setCreateBy(actorUserId);
        event.setCreateTime(now);
        event.setUpdateBy(actorUserId);
        event.setUpdateTime(now);
        event.setDelFlag(0);
        eventMapper.insert(event);
        syncRoom(event, bo.getRoomId(), actorUserId, now);
        if (event.getRoomId() != null) {
            eventMapper.updateById(event);
        }
        List<Long> attendees = normalizeAttendees(bo.getAttendeeIds(), actorUserId);
        for (Long userId : attendees) {
            attendeeMapper.insert(event.getId(), userId, now);
            outboxWriter.writeTodo("CL-INV-" + event.getId() + "-" + userId, userId,
                "日程邀请：" + event.getTitle(), "你被邀请参加日程「" + event.getTitle() + "」",
                "calendar", event.getId());
        }
        expandRecurrence(event, bo, actorUserId, now);
        return toVo(event, actorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventVo update(Long id, EventBo bo, Long actorUserId) {
        OaCalendarEvent event = require(id);
        if (!CollaborationAccessPolicy.canManageEvent(actorUserId, event.getOrganizerId(), isSuperAdmin())) {
            throw new ServiceException("CL_FORBIDDEN 仅组织者可修改日程", 403);
        }
        if (event.getStatus() != EventStatus.NORMAL.code()) {
            throw new ServiceException("CL_STATE_CONFLICT 已取消日程不可修改", 409);
        }
        Date start = TimeUtil.parseInstant(bo.getStartTime(), "startTime");
        Date end = TimeUtil.parseInstant(bo.getEndTime(), "endTime");
        TimeUtil.validateRange(start, end);
        Date now = new Date();
        event.setTitle(bo.getTitle().trim());
        event.setDescription(bo.getDescription());
        event.setEventType(Integer.valueOf(bo.getEventType() == null ? "1" : bo.getEventType()));
        event.setStartTime(start);
        event.setEndTime(end);
        event.setIsAllDay(bo.getIsAllDay() == null ? event.getIsAllDay() : bo.getIsAllDay());
        event.setLocation(bo.getLocation());
        event.setVisibility(Visibility.from(bo.getVisibility() == null ? null : Integer.valueOf(bo.getVisibility())).code());
        event.setRemindMinutes(bo.getRemindMinutes() == null ? event.getRemindMinutes() : bo.getRemindMinutes());
        event.setUpdateBy(actorUserId);
        event.setUpdateTime(now);
        syncRoom(event, bo.getRoomId(), actorUserId, now);
        int nextVersion = event.getLockVersion() == null ? 1 : event.getLockVersion() + 1;
        event.setLockVersion(null);
        int updated = eventMapper.update(event, new LambdaUpdateWrapper<OaCalendarEvent>()
            .eq(OaCalendarEvent::getId, id)
            .eq(OaCalendarEvent::getLockVersion, requireVersion(bo.getLockVersion()))
            .set(OaCalendarEvent::getLockVersion, nextVersion));
        if (updated == 0) {
            throw new ServiceException("VERSION_CONFLICT 日程已被他人修改", 409);
        }
        event.setLockVersion(nextVersion);
        syncAttendees(event, bo.getAttendeeIds(), actorUserId, now);
        return toVo(event, actorUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long actorUserId) {
        delete(id, actorUserId, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long actorUserId, boolean deleteSeries) {
        OaCalendarEvent event = require(id);
        if (!CollaborationAccessPolicy.canManageEvent(actorUserId, event.getOrganizerId(), isSuperAdmin())) {
            throw new ServiceException("CL_FORBIDDEN 仅组织者可删除日程", 403);
        }
        Date now = new Date();
        if (deleteSeries && event.getSeriesId() != null && event.getSeriesId() > 0) {
            long seriesId = event.getSeriesId();
            List<OaCalendarEvent> instances = eventMapper.selectList(new LambdaQueryWrapper<OaCalendarEvent>()
                .eq(OaCalendarEvent::getSeriesId, seriesId)
                .eq(OaCalendarEvent::getDelFlag, 0));
            for (OaCalendarEvent inst : instances) {
                releaseBooking(inst, actorUserId, now);
                eventMapper.update(null, new LambdaUpdateWrapper<OaCalendarEvent>()
                    .eq(OaCalendarEvent::getId, inst.getId())
                    .set(OaCalendarEvent::getDelFlag, 1)
                    .set(OaCalendarEvent::getUpdateTime, now));
            }
            notifyAttendees(event, "日程删除", "系列日程「" + event.getTitle() + "」已被删除", "CL-DELS-" + seriesId, now);
            return;
        }
        releaseBooking(event, actorUserId, now);
        int deleted = eventMapper.update(null, new LambdaUpdateWrapper<OaCalendarEvent>()
            .eq(OaCalendarEvent::getId, id)
            .set(OaCalendarEvent::getDelFlag, 1)
            .set(OaCalendarEvent::getUpdateTime, now));
        if (deleted == 0) {
            throw new ServiceException("CL_EVENT_NOT_FOUND 日程不存在", 404);
        }
        notifyAttendees(event, "日程删除", "日程「" + event.getTitle() + "」已被删除", "CL-DEL-" + id, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventVo respond(Long id, boolean accept, Long actorUserId) {
        OaCalendarEvent event = require(id);
        if (attendeeMapper.exists(id, actorUserId) == 0) {
            throw new ServiceException("CL_FORBIDDEN 仅参与人可响应邀请", 403);
        }
        if (event.getStatus() != EventStatus.NORMAL.code()) {
            throw new ServiceException("CL_STATE_CONFLICT 已取消日程不可响应", 409);
        }
        String response = accept ? AttendeeResponse.ACCEPTED.code() : AttendeeResponse.REJECTED.code();
        attendeeMapper.updateResponse(id, actorUserId, response, new Date());
        outboxWriter.writeNotice("CL-RESP-" + id + "-" + actorUserId + "-" + response, event.getOrganizerId(),
            "日程响应", identityMapper.selectNickName(actorUserId) + " " + (accept ? "接受" : "拒绝")
                + "了日程「" + event.getTitle() + "」", "calendar", id);
        return toVo(event, actorUserId);
    }

    // ------------------------------------------------------------ internal

    /** 可见性（docs/17 第 3 步）：组织者与参与人始终可见；私有/参与人范围外不可见 */
    private boolean visible(OaCalendarEvent event, Long actorUserId) {
        Set<Long> attendees = new LinkedHashSet<>(attendeeMapper.selectUserIds(event.getId()));
        return CollaborationAccessPolicy.canViewEvent(actorUserId, event.getOrganizerId(), attendees,
            Visibility.from(event.getVisibility()), identityMapper.selectDeptId(actorUserId),
            identityMapper.selectDeptId(event.getOrganizerId()), isSuperAdmin());
    }

    /** 参与人规范化：去重、去掉组织者、校验账号存在 */
    private List<Long> normalizeAttendees(List<String> attendeeIds, Long organizerId) {
        Set<Long> ids = new LinkedHashSet<>();
        if (attendeeIds != null) {
            for (String raw : attendeeIds) {
                if (raw != null && !raw.isBlank()) {
                    ids.add(Long.valueOf(raw.trim()));
                }
            }
        }
        ids.remove(organizerId);
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> existing = identityMapper.selectExistingUserIds(new ArrayList<>(ids));
        if (existing.size() != ids.size()) {
            throw new ServiceException("CL_ATTENDEE_INVALID 参与人账号不存在", 400);
        }
        return existing;
    }

    /** 参与人同步：新增/移除保留记录（REMOVED）并通知；重复添加不重复通知（docs/17 通知重复） */
    private void syncAttendees(OaCalendarEvent event, List<String> attendeeIds, Long actorUserId, Date now) {
        List<Long> target = normalizeAttendees(attendeeIds, event.getOrganizerId());
        Set<Long> current = new LinkedHashSet<>(attendeeMapper.selectUserIds(event.getId()));
        for (Long userId : target) {
            if (!current.contains(userId)) {
                if (attendeeMapper.updateResponse(event.getId(), userId, AttendeeResponse.PENDING.code(), now) == 0) {
                    attendeeMapper.insert(event.getId(), userId, now);
                }
                outboxWriter.writeTodo("CL-INV-" + event.getId() + "-" + userId, userId,
                    "日程邀请：" + event.getTitle(), "你被邀请参加日程「" + event.getTitle() + "」",
                    "calendar", event.getId());
            }
        }
        for (Long userId : current) {
            if (!target.contains(userId)) {
                attendeeMapper.updateResponse(event.getId(), userId, "REMOVED", now);
                outboxWriter.writeNotice("CL-RMV-" + event.getId() + "-" + userId, userId,
                    "日程移除", "你已被移出日程「" + event.getTitle() + "」", "calendar", event.getId());
            }
        }
    }

    /** 会议室联动（docs/04 14.3）：同事务锁定时段、拒绝重叠，日程与预约一一对应 */
    private void syncRoom(OaCalendarEvent event, String roomId, Long actorUserId, Date now) {
        Long newRoomId = roomId == null || roomId.isBlank() ? null : Long.valueOf(roomId.trim());
        Long oldRoomId = event.getId() == null ? null : currentBookingRoom(event.getId());
        if (newRoomId == null) {
            if (oldRoomId != null) {
                releaseBooking(event, actorUserId, now);
                event.setRoomId(null);
            }
            return;
        }
        OaMeetingRoom room = roomMapper.selectById(newRoomId);
        if (room == null || room.getDelFlag() != null && room.getDelFlag() != 0) {
            throw new ServiceException("CL_ROOM_NOT_FOUND 会议室不存在", 404);
        }
        if (room.getStatus() == null || room.getStatus() != 1) {
            throw new ServiceException("CL_ROOM_UNAVAILABLE 会议室维护中不可预约", 409);
        }
        Long conflictId = null;
        List<Long> overlapping = bookingMapper.selectOverlappingForUpdate(newRoomId, event.getStartTime(), event.getEndTime());
        for (Long bookingId : overlapping) {
            OaRoomBooking existing = bookingMapper.selectById(bookingId);
            if (existing != null && !java.util.Objects.equals(existing.getEventId(), event.getId())) {
                conflictId = bookingId;
                break;
            }
        }
        if (conflictId != null) {
            throw new ServiceException("CL_ROOM_CONFLICT 会议室时段冲突", 409);
        }
        OaRoomBooking booking = bookingMapper.selectOne(new LambdaQueryWrapper<OaRoomBooking>()
            .eq(OaRoomBooking::getEventId, event.getId()));
        if (booking == null) {
            booking = new OaRoomBooking();
            booking.setLockVersion(0);
            booking.setRoomId(newRoomId);
            booking.setEventId(event.getId());
            booking.setTitle(event.getTitle());
            booking.setBookerId(actorUserId);
            booking.setStartTime(event.getStartTime());
            booking.setEndTime(event.getEndTime());
            booking.setStatus(BookingStatus.BOOKED.code());
            booking.setCreateTime(now);
            booking.setUpdateTime(now);
            bookingMapper.insert(booking);
        } else {
            booking.setRoomId(newRoomId);
            booking.setTitle(event.getTitle());
            booking.setStartTime(event.getStartTime());
            booking.setEndTime(event.getEndTime());
            booking.setStatus(BookingStatus.BOOKED.code());
            booking.setUpdateTime(now);
            bookingMapper.updateById(booking);
        }
        event.setRoomId(newRoomId);
    }

    private Long currentBookingRoom(Long eventId) {
        OaRoomBooking booking = bookingMapper.selectOne(new LambdaQueryWrapper<OaRoomBooking>()
            .eq(OaRoomBooking::getEventId, eventId));
        return booking == null ? null : booking.getRoomId();
    }

    /** 取消/删除时释放预约但保留历史 */
    private void releaseBooking(OaCalendarEvent event, Long actorUserId, Date now) {
        OaRoomBooking booking = bookingMapper.selectOne(new LambdaQueryWrapper<OaRoomBooking>()
            .eq(OaRoomBooking::getEventId, event.getId()));
        if (booking != null && (booking.getStatus() == BookingStatus.BOOKED.code()
            || booking.getStatus() == BookingStatus.CHECKED_IN.code())) {
            bookingMapper.update(null, new LambdaUpdateWrapper<OaRoomBooking>()
                .eq(OaRoomBooking::getId, booking.getId())
                .in(OaRoomBooking::getStatus, BookingStatus.BOOKED.code(), BookingStatus.CHECKED_IN.code())
                .set(OaRoomBooking::getStatus, BookingStatus.RELEASED.code())
                .set(OaRoomBooking::getUpdateTime, now));
        }
    }

    private void notifyAttendees(OaCalendarEvent event, String title, String content, String keyPrefix, Date now) {
        for (Long userId : attendeeMapper.selectUserIds(event.getId())) {
            if ("REMOVED".equals(attendeeMapper.selectResponseStatus(event.getId(), userId))) {
                continue;
            }
            outboxWriter.writeNotice(keyPrefix + "-" + userId, userId, title, content, "calendar", event.getId());
        }
    }

    /** 展开重复日程实例（预物化，≤12 个月且 ≤365 条） */
    private void expandRecurrence(OaCalendarEvent master, EventBo bo, Long actorUserId, Date now) {
        if (bo.getRepeatRule() == null || bo.getRepeatRule().isBlank()) {
            return;
        }
        RecurrenceRule rule = RecurrenceRule.parse(bo.getRepeatRule());
        master.setRepeatRule(bo.getRepeatRule().trim());
        master.setSeriesId(0L);
        if (bo.getRepeatUntil() != null && !bo.getRepeatUntil().isBlank()) {
            master.setRepeatUntil(TimeUtil.parseInstant(bo.getRepeatUntil(), "repeatUntil"));
        }
        if (bo.getRepeatCount() != null) {
            master.setRepeatCount(bo.getRepeatCount());
        }
        master.setIsException(0);
        eventMapper.updateById(master);

        LocalDate startDate = RecurrenceRule.toLocalDate(master.getStartTime());
        int maxInstances = master.getRepeatCount() != null ? master.getRepeatCount() : 365;
        List<LocalDate> dates = rule.expand(startDate, maxInstances);
        long durationMs = master.getEndTime().getTime() - master.getStartTime().getTime();
        for (LocalDate date : dates) {
            OaCalendarEvent inst = new OaCalendarEvent();
            inst.setLockVersion(0);
            inst.setTitle(master.getTitle());
            inst.setDescription(master.getDescription());
            inst.setEventType(master.getEventType());
            inst.setStartTime(RecurrenceRule.toDate(date));
            inst.setEndTime(new Date(RecurrenceRule.toDate(date).getTime() + durationMs));
            inst.setIsAllDay(master.getIsAllDay());
            inst.setLocation(master.getLocation());
            inst.setOrganizerId(master.getOrganizerId());
            inst.setRoomId(master.getRoomId());
            inst.setVisibility(master.getVisibility());
            inst.setRemindMinutes(master.getRemindMinutes());
            inst.setRepeatRule(null);
            inst.setSeriesId(master.getId());
            inst.setIsException(0);
            inst.setStatus(EventStatus.NORMAL.code());
            inst.setCreateBy(actorUserId);
            inst.setCreateTime(now);
            inst.setUpdateBy(actorUserId);
            inst.setUpdateTime(now);
            inst.setDelFlag(0);
            eventMapper.insert(inst);
            for (Long userId : attendeeMapper.selectUserIds(master.getId())) {
                attendeeMapper.insert(inst.getId(), userId, now);
            }
        }
    }

    private OaCalendarEvent require(Long id) {
        OaCalendarEvent event = eventMapper.selectById(id);
        if (event == null || event.getDelFlag() != null && event.getDelFlag() != 0) {
            throw new ServiceException("CL_EVENT_NOT_FOUND 日程不存在", 404);
        }
        return event;
    }

    private Integer requireVersion(Integer lockVersion) {
        return lockVersion == null ? -1 : lockVersion;
    }

    private boolean isSuperAdmin() {
        return org.dromara.common.satoken.utils.LoginHelper.isSuperAdmin();
    }

    private EventVo toVo(OaCalendarEvent event, Long actorUserId) {
        EventVo vo = new EventVo();
        vo.setId(event.getId());
        vo.setLockVersion(event.getLockVersion());
        vo.setTitle(event.getTitle());
        vo.setDescription(event.getDescription());
        vo.setEventType(event.getEventType());
        vo.setStartTime(TimeUtil.format(event.getStartTime()));
        vo.setEndTime(TimeUtil.format(event.getEndTime()));
        vo.setIsAllDay(event.getIsAllDay());
        vo.setLocation(event.getLocation());
        vo.setOrganizerId(event.getOrganizerId());
        vo.setOrganizerName(identityMapper.selectNickName(event.getOrganizerId()));
        vo.setRoomId(event.getRoomId());
        if (event.getRoomId() != null) {
            OaMeetingRoom room = roomMapper.selectById(event.getRoomId());
            vo.setRoomName(room == null ? null : room.getName());
        }
        vo.setVisibility(event.getVisibility());
        vo.setRemindMinutes(event.getRemindMinutes());
        vo.setStatus(event.getStatus());
        vo.setRepeatRule(event.getRepeatRule());
        vo.setSeriesId(event.getSeriesId());
        vo.setIsException(event.getIsException());
        List<EventVo.Attendee> attendees = new ArrayList<>();
        String myResponse = null;
        for (Long userId : attendeeMapper.selectUserIds(event.getId())) {
            String response = attendeeMapper.selectResponseStatus(event.getId(), userId);
            if ("REMOVED".equals(response)) {
                continue;
            }
            EventVo.Attendee attendee = new EventVo.Attendee();
            attendee.setUserId(userId);
            attendee.setNickname(identityMapper.selectNickName(userId));
            attendee.setResponseStatus(response);
            attendees.add(attendee);
            if (userId.equals(actorUserId)) {
                myResponse = response;
            }
        }
        vo.setAttendees(attendees);
        if (actorUserId != null && actorUserId.equals(event.getOrganizerId())) {
            myResponse = AttendeeResponse.ACCEPTED.code();
        }
        vo.setMyResponse(myResponse);
        vo.setCanManage(CollaborationAccessPolicy.canManageEvent(actorUserId, event.getOrganizerId(), isSuperAdmin()));
        vo.setCreateTime(TimeUtil.format(event.getCreateTime()));
        vo.setUpdateTime(TimeUtil.format(event.getUpdateTime()));
        return vo;
    }
}

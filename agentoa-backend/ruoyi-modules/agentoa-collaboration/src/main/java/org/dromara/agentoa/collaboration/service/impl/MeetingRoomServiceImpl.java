package org.dromara.agentoa.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.collaboration.domain.OaMeetingRoom;
import org.dromara.agentoa.collaboration.domain.OaRoomBooking;
import org.dromara.agentoa.collaboration.domain.bo.RoomBo;
import org.dromara.agentoa.collaboration.domain.enums.BookingStatus;
import org.dromara.agentoa.collaboration.domain.enums.RoomStatus;
import org.dromara.agentoa.collaboration.domain.vo.RoomVo;
import org.dromara.agentoa.collaboration.mapper.OaMeetingRoomMapper;
import org.dromara.agentoa.collaboration.mapper.OaRoomBookingMapper;
import org.dromara.agentoa.collaboration.service.IMeetingRoomService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** 会议室维护实现：删除前检查生效预约，维护中不可预约。 */
@Service
@RequiredArgsConstructor
public class MeetingRoomServiceImpl implements IMeetingRoomService {

    private final OaMeetingRoomMapper roomMapper;
    private final OaRoomBookingMapper bookingMapper;

    @Override
    public PageVo<RoomVo> list(String keyword, Integer pageNum, Integer pageSize) {
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        LambdaQueryWrapper<OaMeetingRoom> wrapper = new LambdaQueryWrapper<OaMeetingRoom>()
            .eq(OaMeetingRoom::getDelFlag, 0);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(OaMeetingRoom::getName, keyword.trim());
        }
        wrapper.orderByAsc(OaMeetingRoom::getId);
        IPage<OaMeetingRoom> result = roomMapper.selectPage(new Page<>(num, size), wrapper);
        List<RoomVo> records = new ArrayList<>();
        for (OaMeetingRoom room : result.getRecords()) {
            records.add(toVo(room));
        }
        return PageVo.of(records, result.getTotal(), num, size);
    }

    @Override
    public List<RoomVo> all() {
        List<OaMeetingRoom> rooms = roomMapper.selectList(new LambdaQueryWrapper<OaMeetingRoom>()
            .eq(OaMeetingRoom::getDelFlag, 0)
            .orderByAsc(OaMeetingRoom::getId));
        List<RoomVo> records = new ArrayList<>();
        for (OaMeetingRoom room : rooms) {
            records.add(toVo(room));
        }
        return records;
    }

    @Override
    public RoomVo get(Long id) {
        return toVo(require(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoomVo create(RoomBo bo, Long actorUserId) {
        Date now = new Date();
        OaMeetingRoom room = new OaMeetingRoom();
        room.setName(bo.getName().trim());
        room.setLocation(bo.getLocation());
        room.setCapacity(bo.getCapacity());
        room.setEquipment(bo.getEquipment());
        room.setStatus(bo.getStatus() == null ? RoomStatus.AVAILABLE.code() : Integer.valueOf(bo.getStatus()));
        room.setCreateBy(actorUserId);
        room.setCreateTime(now);
        room.setUpdateBy(actorUserId);
        room.setUpdateTime(now);
        room.setDelFlag(0);
        roomMapper.insert(room);
        return toVo(room);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoomVo update(Long id, RoomBo bo, Long actorUserId) {
        OaMeetingRoom room = require(id);
        room.setName(bo.getName().trim());
        room.setLocation(bo.getLocation());
        room.setCapacity(bo.getCapacity());
        room.setEquipment(bo.getEquipment());
        if (bo.getStatus() != null) {
            room.setStatus(Integer.valueOf(bo.getStatus()));
        }
        room.setUpdateBy(actorUserId);
        room.setUpdateTime(new Date());
        roomMapper.updateById(room);
        return toVo(room);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long actorUserId) {
        OaMeetingRoom room = require(id);
        Long active = bookingMapper.selectCount(new LambdaQueryWrapper<OaRoomBooking>()
            .eq(OaRoomBooking::getRoomId, id)
            .in(OaRoomBooking::getStatus, BookingStatus.BOOKED.code(), BookingStatus.CHECKED_IN.code())
            .gt(OaRoomBooking::getEndTime, new Date()));
        if (active != null && active > 0) {
            throw new ServiceException("CL_ROOM_IN_USE 会议室存在生效预约，不可删除", 409);
        }
        roomMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OaMeetingRoom>()
            .eq(OaMeetingRoom::getId, id)
            .set(OaMeetingRoom::getDelFlag, 1)
            .set(OaMeetingRoom::getUpdateTime, new Date()));
    }

    private OaMeetingRoom require(Long id) {
        OaMeetingRoom room = roomMapper.selectById(id);
        if (room == null || room.getDelFlag() != null && room.getDelFlag() != 0) {
            throw new ServiceException("CL_ROOM_NOT_FOUND 会议室不存在", 404);
        }
        return room;
    }

    private RoomVo toVo(OaMeetingRoom room) {
        RoomVo vo = new RoomVo();
        vo.setId(room.getId());
        vo.setName(room.getName());
        vo.setLocation(room.getLocation());
        vo.setCapacity(room.getCapacity());
        vo.setEquipment(room.getEquipment());
        vo.setStatus(room.getStatus());
        vo.setCreateTime(org.dromara.agentoa.collaboration.service.support.TimeUtil.format(room.getCreateTime()));
        vo.setUpdateTime(org.dromara.agentoa.collaboration.service.support.TimeUtil.format(room.getUpdateTime()));
        return vo;
    }
}

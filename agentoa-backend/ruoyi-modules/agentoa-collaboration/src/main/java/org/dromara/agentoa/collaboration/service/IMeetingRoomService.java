package org.dromara.agentoa.collaboration.service;

import org.dromara.agentoa.collaboration.domain.bo.RoomBo;
import org.dromara.agentoa.collaboration.domain.vo.RoomVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.List;

/** 会议室维护（docs/05 9.2，cl:room:* 授权） */
public interface IMeetingRoomService {

    PageVo<RoomVo> list(String keyword, Integer pageNum, Integer pageSize);

    List<RoomVo> all();

    RoomVo get(Long id);

    RoomVo create(RoomBo bo, Long actorUserId);

    RoomVo update(Long id, RoomBo bo, Long actorUserId);

    void delete(Long id, Long actorUserId);
}

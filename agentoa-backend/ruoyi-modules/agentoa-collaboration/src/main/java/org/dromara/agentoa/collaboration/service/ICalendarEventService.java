package org.dromara.agentoa.collaboration.service;

import org.dromara.agentoa.collaboration.domain.bo.EventBo;
import org.dromara.agentoa.collaboration.domain.bo.EventPageQuery;
import org.dromara.agentoa.collaboration.domain.vo.EventVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

/** 日程服务（docs/05 9.1）：可见范围、参与人邀请响应、会议室联动。 */
public interface ICalendarEventService {

    PageVo<EventVo> list(EventPageQuery query, Long actorUserId);

    EventVo detail(Long id, Long actorUserId);

    EventVo create(EventBo bo, Long actorUserId);

    EventVo update(Long id, EventBo bo, Long actorUserId);

    void delete(Long id, Long actorUserId);

    /** 删除日程（deleteSeries=true 删除整个系列，false 仅删除当前实例） */
    void delete(Long id, Long actorUserId, boolean deleteSeries);

    /** 接受/拒绝邀请（仅参与人） */
    EventVo respond(Long id, boolean accept, Long actorUserId);
}

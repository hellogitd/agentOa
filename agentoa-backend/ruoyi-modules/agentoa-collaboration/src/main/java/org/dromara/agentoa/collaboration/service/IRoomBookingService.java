package org.dromara.agentoa.collaboration.service;

import org.dromara.agentoa.collaboration.domain.bo.BookingBo;
import org.dromara.agentoa.collaboration.domain.bo.CollabPageQuery;
import org.dromara.agentoa.collaboration.domain.vo.BookingVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

/** 会议室预约（docs/05 9.2）：行锁防重叠，Idempotency-Key 幂等，取消释放但保留历史。 */
public interface IRoomBookingService {

    PageVo<BookingVo> list(Long roomId, String start, String end, CollabPageQuery query);

    BookingVo detail(Long id, Long actorUserId);

    BookingVo book(Long roomId, BookingBo bo, String idempotencyKey, Long actorUserId);

    BookingVo checkin(Long id, Long actorUserId);

    BookingVo cancel(Long id, Long actorUserId);
}

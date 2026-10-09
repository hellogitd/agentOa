package org.dromara.agentoa.collaboration.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.agentoa.collaboration.domain.OaRoomBooking;
import org.dromara.agentoa.collaboration.domain.vo.BookingVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.Date;
import java.util.List;

public interface OaRoomBookingMapper extends BaseMapperPlus<OaRoomBooking, BookingVo> {

    /**
     * 重叠检查（docs/17 第 2 步）：行锁锁定重叠区间，条件更新之外的数据库级防并发手段，
     * 不依赖 Redis 锁。左闭右开区间重叠：start_time < end AND end_time > start。
     */
    @Select("SELECT id FROM oa_room_booking WHERE room_id = #{roomId} AND status IN (1, 2) "
        + "AND start_time < #{endTime} AND end_time > #{startTime} FOR UPDATE")
    List<Long> selectOverlappingForUpdate(@Param("roomId") Long roomId,
                                          @Param("startTime") Date startTime,
                                          @Param("endTime") Date endTime);
}

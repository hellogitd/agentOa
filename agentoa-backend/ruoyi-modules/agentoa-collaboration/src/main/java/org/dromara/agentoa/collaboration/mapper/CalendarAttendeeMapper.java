package org.dromara.agentoa.collaboration.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/** 日程参与人访问（复合主键表） */
public interface CalendarAttendeeMapper {

    @Insert("INSERT INTO oa_calendar_attendee(event_id, user_id, response_status, create_time) "
        + "VALUES(#{eventId}, #{userId}, 'PENDING', #{now})")
    int insert(@Param("eventId") Long eventId, @Param("userId") Long userId, @Param("now") Date now);

    @Select("SELECT user_id FROM oa_calendar_attendee WHERE event_id = #{eventId}")
    List<Long> selectUserIds(@Param("eventId") Long eventId);

    @Select("SELECT response_status FROM oa_calendar_attendee WHERE event_id = #{eventId} AND user_id = #{userId}")
    String selectResponseStatus(@Param("eventId") Long eventId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM oa_calendar_attendee WHERE event_id = #{eventId} AND user_id = #{userId}")
    long exists(@Param("eventId") Long eventId, @Param("userId") Long userId);

    @Update("UPDATE oa_calendar_attendee SET response_status = #{response}, update_time = #{now} "
        + "WHERE event_id = #{eventId} AND user_id = #{userId}")
    int updateResponse(@Param("eventId") Long eventId, @Param("userId") Long userId,
                       @Param("response") String response, @Param("now") Date now);

    @Delete("DELETE FROM oa_calendar_attendee WHERE event_id = #{eventId} AND user_id = #{userId}")
    int delete(@Param("eventId") Long eventId, @Param("userId") Long userId);

    @Delete("DELETE FROM oa_calendar_attendee WHERE event_id = #{eventId}")
    int deleteByEvent(@Param("eventId") Long eventId);
}

package org.dromara.agentoa.notice.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 通知偏好访问（复合主键表）。
 */
public interface NoticePreferenceMapper {

    @Select("SELECT msg_type AS msgType, enabled FROM oa_notification_preference WHERE user_id = #{userId}")
    List<Map<String, Object>> selectByUser(@Param("userId") Long userId);

    @Select("SELECT enabled FROM oa_notification_preference WHERE user_id = #{userId} AND msg_type = #{msgType}")
    Integer selectEnabled(@Param("userId") Long userId, @Param("msgType") String msgType);

    @Update("UPDATE oa_notification_preference SET enabled = #{enabled}, update_time = #{now} "
        + "WHERE user_id = #{userId} AND msg_type = #{msgType}")
    int update(@Param("userId") Long userId, @Param("msgType") String msgType,
               @Param("enabled") int enabled, @Param("now") Date now);

    @Insert("INSERT INTO oa_notification_preference(user_id, msg_type, enabled, update_time) "
        + "VALUES(#{userId}, #{msgType}, #{enabled}, #{now})")
    int insert(@Param("userId") Long userId, @Param("msgType") String msgType,
               @Param("enabled") int enabled, @Param("now") Date now);
}

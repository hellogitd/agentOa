package org.dromara.agentoa.notice.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
 * 公告受众快照访问（复合主键表）。
 */
public interface NoticeAudienceMapper {

    @Insert("INSERT INTO oa_announcement_audience(notice_id, user_id, create_time) VALUES(#{noticeId}, #{userId}, #{now})")
    int insert(@Param("noticeId") Long noticeId, @Param("userId") Long userId, @Param("now") Date now);

    @Select("SELECT user_id FROM oa_announcement_audience WHERE notice_id = #{noticeId}")
    List<Long> selectUserIds(@Param("noticeId") Long noticeId);

    @Select("SELECT COUNT(*) FROM oa_announcement_audience WHERE notice_id = #{noticeId}")
    long count(@Param("noticeId") Long noticeId);

    @Select("SELECT COUNT(*) FROM oa_announcement_audience WHERE notice_id = #{noticeId} AND user_id = #{userId}")
    long exists(@Param("noticeId") Long noticeId, @Param("userId") Long userId);

    @Delete("DELETE FROM oa_announcement_audience WHERE notice_id = #{noticeId}")
    int deleteByNotice(@Param("noticeId") Long noticeId);
}

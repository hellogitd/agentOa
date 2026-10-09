package org.dromara.agentoa.collaboration.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/** 任务协作者访问（复合主键表） */
public interface TaskMemberMapper {

    @Insert("INSERT INTO oa_task_member(task_id, user_id, create_by, create_time) "
        + "VALUES(#{taskId}, #{userId}, #{createBy}, #{now})")
    int insert(@Param("taskId") Long taskId, @Param("userId") Long userId,
               @Param("createBy") Long createBy, @Param("now") Date now);

    @Select("SELECT user_id FROM oa_task_member WHERE task_id = #{taskId}")
    List<Long> selectUserIds(@Param("taskId") Long taskId);

    @Select("SELECT task_id FROM oa_task_member WHERE user_id = #{userId}")
    List<Long> selectTaskIds(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM oa_task_member WHERE task_id = #{taskId} AND user_id = #{userId}")
    long exists(@Param("taskId") Long taskId, @Param("userId") Long userId);

    @Delete("DELETE FROM oa_task_member WHERE task_id = #{taskId} AND user_id = #{userId}")
    int delete(@Param("taskId") Long taskId, @Param("userId") Long userId);

    @Delete("DELETE FROM oa_task_member WHERE task_id = #{taskId}")
    int deleteByTask(@Param("taskId") Long taskId);
}

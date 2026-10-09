package org.dromara.agentoa.collaboration.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 账号只读查询（昵称与部门，用于参与人/协作人/通知） */
public interface CollaborationIdentityReadMapper {

    @Select("SELECT nick_name FROM sys_user WHERE user_id = #{userId} LIMIT 1")
    String selectNickName(@Param("userId") Long userId);

    @Select("SELECT dept_id FROM sys_user WHERE user_id = #{userId} LIMIT 1")
    Long selectDeptId(@Param("userId") Long userId);

    @Select("<script>SELECT user_id FROM sys_user WHERE del_flag = '0' AND status = '0' AND user_id IN "
        + "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Long> selectExistingUserIds(@Param("userIds") List<Long> userIds);
}

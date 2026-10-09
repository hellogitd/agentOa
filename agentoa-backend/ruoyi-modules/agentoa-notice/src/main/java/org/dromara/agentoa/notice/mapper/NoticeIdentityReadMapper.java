package org.dromara.agentoa.notice.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 受众解析与账号只读查询（docs/15 第 3 步）。
 */
public interface NoticeIdentityReadMapper {

    @Select("SELECT user_id FROM sys_user WHERE del_flag = '0' AND status = '0'")
    List<Long> selectActiveUserIds();

    @Select("<script>SELECT user_id FROM sys_user WHERE del_flag = '0' AND status = '0' AND dept_id IN "
        + "<foreach collection='deptIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Long> selectUserIdsByDeptIds(@Param("deptIds") List<Long> deptIds);

    @Select("<script>SELECT DISTINCT u.user_id FROM sys_user u JOIN sys_user_role ur ON u.user_id = ur.user_id "
        + "JOIN sys_role r ON ur.role_id = r.role_id WHERE u.del_flag = '0' AND u.status = '0' AND r.role_key IN "
        + "<foreach collection='roleKeys' item='key' open='(' separator=',' close=')'>#{key}</foreach></script>")
    List<Long> selectUserIdsByRoleKeys(@Param("roleKeys") List<String> roleKeys);

    @Select("<script>SELECT user_id FROM sys_user WHERE del_flag = '0' AND status = '0' AND user_id IN "
        + "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Long> selectExistingUserIds(@Param("userIds") List<Long> userIds);

    @Select("SELECT nick_name FROM sys_user WHERE user_id = #{userId} LIMIT 1")
    String selectNickName(@Param("userId") Long userId);
}

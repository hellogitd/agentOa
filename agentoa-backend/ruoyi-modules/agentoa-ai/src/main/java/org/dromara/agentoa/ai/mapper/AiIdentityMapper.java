package org.dromara.agentoa.ai.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 身份查询（sys_user_role / sys_user），用于角色配额解析与成员展示（docs/21 AI-M1-08、AI-M3-01）。
 */
public interface AiIdentityMapper {

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIds(@Param("userId") Long userId);

    @Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId}")
    List<Long> selectUserIdsByRole(@Param("roleId") Long roleId);

    @Select("<script>SELECT user_id AS userId, user_name AS userName, nick_name AS nickName FROM sys_user "
        + "WHERE user_id IN <foreach item='id' collection='userIds' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String, Object>> selectUserBrief(@Param("userIds") List<Long> userIds);

    /** 通讯录模糊查询（AI-M5-01 user_directory） */
    @Select("SELECT user_id AS userId, user_name AS userName, nick_name AS nickName FROM sys_user "
        + "WHERE (user_name LIKE CONCAT('%', #{keyword}, '%') OR nick_name LIKE CONCAT('%', #{keyword}, '%')) "
        + "AND del_flag = '0' ORDER BY user_id LIMIT 20")
    List<Map<String, Object>> searchUserBrief(@Param("keyword") String keyword);
}

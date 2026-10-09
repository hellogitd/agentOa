package org.dromara.agentoa.knowledge.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 身份只读查询（sys_user / sys_user_role），用于成员展示与 ROLE 主体授权解析。
 */
public interface KnowledgeIdentityMapper {

    @Select("SELECT nick_name FROM sys_user WHERE user_id = #{userId}")
    String selectNickName(@Param("userId") Long userId);

    @Select("<script>SELECT user_id AS userId, nick_name AS nickName FROM sys_user WHERE user_id IN " +
        "<foreach collection='userIds' item='uid' open='(' separator=',' close=')'>#{uid}</foreach></script>")
    List<Map<String, Object>> selectNickNames(@Param("userIds") List<Long> userIds);

    @Select("SELECT COUNT(*) FROM sys_user WHERE user_id = #{userId} AND del_flag = '0'")
    long existsUser(@Param("userId") Long userId);

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIds(@Param("userId") Long userId);
}

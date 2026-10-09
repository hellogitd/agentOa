package org.dromara.agentoa.workflow.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.dromara.agentoa.workflow.domain.vo.AssignableUserVo;

import java.util.List;

/**
 * 办理人解析只读查询：数据源为 HR 组织关系与系统账号（docs/12 办理人解析器）。
 */
public interface WorkflowIdentityReadMapper {

    @Select("SELECT direct_leader_id FROM oa_employee WHERE user_id = #{userId} AND direct_leader_id IS NOT NULL LIMIT 1")
    Long selectDirectLeaderUserId(@Param("userId") Long userId);

    @Select("SELECT id FROM oa_employee WHERE user_id = #{userId} LIMIT 1")
    Long selectEmployeeId(@Param("userId") Long userId);

    @Select("SELECT dept_id FROM oa_employee WHERE user_id = #{userId} LIMIT 1")
    Long selectEmployeeDeptId(@Param("userId") Long userId);

    @Select("SELECT dept_id FROM sys_user WHERE user_id = #{userId} LIMIT 1")
    Long selectUserDeptId(@Param("userId") Long userId);

    @Select("SELECT leader FROM sys_dept WHERE dept_id = #{deptId} AND leader IS NOT NULL LIMIT 1")
    Long selectDeptLeaderUserId(@Param("deptId") Long deptId);

    @Select("SELECT u.user_id FROM sys_user u JOIN sys_user_role ur ON u.user_id = ur.user_id "
        + "JOIN sys_role r ON ur.role_id = r.role_id WHERE r.role_key = #{roleKey} AND u.del_flag = '0' AND u.status = '0'")
    List<Long> selectUserIdsByRoleKey(@Param("roleKey") String roleKey);

    @Select("SELECT user_name FROM sys_user WHERE user_id = #{userId} LIMIT 1")
    String selectUserName(@Param("userId") Long userId);

    @Select("SELECT nick_name FROM sys_user WHERE user_id = #{userId} LIMIT 1")
    String selectNickName(@Param("userId") Long userId);

    /** 可选办理人目录：只回启用账号，keyword 为空时不过滤（MySQL/H2 兼容：LIMIT + OFFSET） */
    @Results(id = "assignableUser", value = {
        @Result(column = "user_id", property = "userId"),
        @Result(column = "name", property = "name"),
        @Result(column = "dept_name", property = "deptName")
    })
    @Select("SELECT u.user_id, COALESCE(NULLIF(u.nick_name, ''), u.user_name) AS name, d.dept_name "
        + "FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id = d.dept_id "
        + "WHERE u.del_flag = '0' AND u.status = '0' "
        + "AND (#{keyword} = '' OR u.user_name LIKE CONCAT('%', #{keyword}, '%') "
        + "OR u.nick_name LIKE CONCAT('%', #{keyword}, '%')) "
        + "ORDER BY u.user_id ASC LIMIT #{limit} OFFSET #{offset}")
    List<AssignableUserVo> selectAssignableUsers(@Param("keyword") String keyword,
                                                @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM sys_user u WHERE u.del_flag = '0' AND u.status = '0' "
        + "AND (#{keyword} = '' OR u.user_name LIKE CONCAT('%', #{keyword}, '%') "
        + "OR u.nick_name LIKE CONCAT('%', #{keyword}, '%'))")
    long countAssignableUsers(@Param("keyword") String keyword);

    /** 校验候选 userId 是否存在且启用（USER_SELECT 选人/WHITELIST 入库校验用） */
    @Select("<script>SELECT user_id FROM sys_user WHERE del_flag = '0' AND status = '0' "
        + "AND user_id IN <foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Long> selectEnabledUserIds(@Param("userIds") List<Long> userIds);

    /** 启用角色 key 列表（ROLE:key 角色存在性校验，替换固定白名单） */
    @Select("SELECT role_key FROM sys_role WHERE del_flag = '0' AND status = '0'")
    List<String> selectEnabledRoleKeys();
}

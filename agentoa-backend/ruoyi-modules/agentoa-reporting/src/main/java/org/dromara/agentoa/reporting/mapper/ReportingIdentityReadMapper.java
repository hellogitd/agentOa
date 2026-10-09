package org.dromara.agentoa.reporting.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 组织与账号只读查询（数据源为 HR 组织关系与系统账号，docs/18 步骤 3 权限与部门范围）。
 */
public interface ReportingIdentityReadMapper {

    @Select("SELECT dept_id FROM sys_user WHERE user_id = #{userId} LIMIT 1")
    Long selectUserDeptId(@Param("userId") Long userId);

    @Select("SELECT nick_name FROM sys_user WHERE user_id = #{userId} LIMIT 1")
    String selectNickName(@Param("userId") Long userId);

    @Select("SELECT dept_name FROM sys_dept WHERE dept_id = #{deptId} LIMIT 1")
    String selectDeptName(@Param("deptId") Long deptId);

    @Select("SELECT r.role_key FROM sys_user_role ur JOIN sys_role r ON ur.role_id = r.role_id "
        + "WHERE ur.user_id = #{userId}")
    List<String> selectRoleKeys(@Param("userId") Long userId);
}

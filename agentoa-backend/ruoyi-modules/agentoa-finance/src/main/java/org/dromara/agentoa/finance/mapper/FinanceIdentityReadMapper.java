package org.dromara.agentoa.finance.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 组织与账号只读查询（数据源为 HR 组织关系与系统账号）。
 */
public interface FinanceIdentityReadMapper {

    @Select("SELECT nick_name FROM sys_user WHERE user_id = #{userId} LIMIT 1")
    String selectNickName(@Param("userId") Long userId);

    @Select("SELECT dept_name FROM sys_dept WHERE dept_id = #{deptId} LIMIT 1")
    String selectDeptName(@Param("deptId") Long deptId);

    @Select("SELECT dept_id FROM sys_user WHERE user_id = #{userId} LIMIT 1")
    Long selectUserDeptId(@Param("userId") Long userId);

    @Select("SELECT original_name FROM sys_file WHERE id = #{fileId} LIMIT 1")
    String selectFileName(@Param("fileId") Long fileId);

    @Select("SELECT t.code FROM oa_expense_type t WHERE t.id = #{id} LIMIT 1")
    String selectExpenseTypeCode(@Param("id") Long id);

    @Select("SELECT u.user_id FROM sys_user u WHERE u.del_flag = '0' AND u.status = '0' AND u.user_id IN "
        + "(SELECT user_id FROM sys_user_role WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_key IN "
        + "('finance','cashier')))")
    List<Long> selectFinanceUserIds();
}

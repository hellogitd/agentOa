package org.dromara.agentoa.finance.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.agentoa.finance.domain.vo.ExpenseReportVo;

import java.time.LocalDate;
import java.util.List;

/**
 * 费用统计只读查询（报销明细 × 承接单 × 部门 × 费用类型）。
 */
public interface FinanceReportMapper {

    @Select("<script>"
        + "SELECT u.dept_id AS deptId, d.dept_name AS deptName, i.expense_type AS expenseType, "
        + "t.name AS expenseTypeName, COUNT(*) AS itemCount, SUM(i.amount) AS totalAmount "
        + "FROM oa_expense_item i "
        + "JOIN oa_reimburse_request c ON i.reimburse_id = c.id "
        + "JOIN sys_user u ON c.user_id = u.user_id "
        + "LEFT JOIN sys_dept d ON u.dept_id = d.dept_id "
        + "LEFT JOIN oa_expense_type t ON i.expense_type = t.code "
        + "WHERE c.status IN (3, 5, 6) "
        + "<if test='startDate != null'> AND i.occur_date &gt;= #{startDate} </if>"
        + "<if test='endDate != null'> AND i.occur_date &lt;= #{endDate} </if>"
        + "<if test='deptId != null'> AND u.dept_id = #{deptId} </if>"
        + "<if test='expenseType != null and expenseType != \"\"'> AND i.expense_type = #{expenseType} </if>"
        + "GROUP BY u.dept_id, d.dept_name, i.expense_type, t.name "
        + "ORDER BY u.dept_id, i.expense_type"
        + "</script>")
    List<ExpenseReportVo> selectExpenseStats(@Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate,
                                             @Param("deptId") Long deptId,
                                             @Param("expenseType") String expenseType);
}

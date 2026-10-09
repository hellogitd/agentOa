package org.dromara.agentoa.finance.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.finance.domain.bo.ReportQueryBo;
import org.dromara.agentoa.finance.domain.policy.FinanceAccessPolicy;
import org.dromara.agentoa.finance.domain.vo.ExpenseReportVo;
import org.dromara.agentoa.finance.mapper.FinanceIdentityReadMapper;
import org.dromara.agentoa.finance.mapper.FinanceReportMapper;
import org.dromara.agentoa.finance.service.IFinanceReportService;
import org.dromara.agentoa.finance.service.support.MoneyUtil;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 费用统计实现：财务/出纳看全量，经理只看本部门（docs/14 权限与安全）。
 */
@Service
@RequiredArgsConstructor
public class FinanceReportServiceImpl implements IFinanceReportService {

    private final FinanceReportMapper reportMapper;
    private final FinanceIdentityReadMapper identityMapper;

    @Override
    public List<ExpenseReportVo> expense(ReportQueryBo query) {
        Long deptId = query.getDeptId();
        if (!FinanceAccessPolicy.canViewAllDepartments(roleKeys())) {
            Long own = identityMapper.selectUserDeptId(LoginHelper.getUserId());
            deptId = own;
        }
        List<ExpenseReportVo> rows = reportMapper.selectExpenseStats(
            query.getStartDate(), query.getEndDate(), deptId, query.getExpenseType());
        for (ExpenseReportVo row : rows) {
            if (row.getTotalAmount() != null) {
                row.setTotalAmount(MoneyUtil.toAmountString(MoneyUtil.toFen(row.getTotalAmount())));
            }
        }
        return rows;
    }

    private Set<String> roleKeys() {
        var loginUser = LoginHelper.getLoginUser();
        return loginUser == null || loginUser.getRolePermission() == null ? Set.of() : loginUser.getRolePermission();
    }
}

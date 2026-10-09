package org.dromara.agentoa.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.finance.domain.bo.ReportQueryBo;
import org.dromara.agentoa.finance.domain.policy.FinanceAccessPolicy;
import org.dromara.agentoa.finance.domain.vo.ExpenseReportVo;
import org.dromara.agentoa.finance.service.IFinanceReportService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;

/**
 * 费用统计（API 规范 docs/14 第 6 步）：部门/费用类型汇总与导出。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/finance/reports")
public class FinanceReportController {

    private final IFinanceReportService reportService;

    @SaCheckPermission("fn:report:list")
    @GetMapping("/expense")
    public R<List<ExpenseReportVo>> expense(ReportQueryBo query) {
        return R.ok(reportService.expense(query));
    }

    @SaCheckPermission("fn:report:export")
    @Log(title = "费用统计导出", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public void export(ReportQueryBo query, HttpServletResponse response) {
        if (!FinanceAccessPolicy.canExport(LoginHelper.isSuperAdmin(), permissions())) {
            throw new ServiceException("WF_FORBIDDEN 无导出权限", 403);
        }
        ExcelUtil.exportExcel(reportService.expense(query), "费用统计", ExpenseReportVo.class, response);
    }

    private Set<String> permissions() {
        var loginUser = LoginHelper.getLoginUser();
        return loginUser == null || loginUser.getMenuPermission() == null ? Set.of() : loginUser.getMenuPermission();
    }
}

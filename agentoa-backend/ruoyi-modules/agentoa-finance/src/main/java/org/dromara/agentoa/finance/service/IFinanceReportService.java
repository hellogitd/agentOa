package org.dromara.agentoa.finance.service;

import org.dromara.agentoa.finance.domain.bo.ReportQueryBo;
import org.dromara.agentoa.finance.domain.vo.ExpenseReportVo;

import java.util.List;

/**
 * 费用统计（docs/14 第 6 步）：按部门/费用类型汇总，可与报销明细对账。
 */
public interface IFinanceReportService {

    List<ExpenseReportVo> expense(ReportQueryBo query);
}

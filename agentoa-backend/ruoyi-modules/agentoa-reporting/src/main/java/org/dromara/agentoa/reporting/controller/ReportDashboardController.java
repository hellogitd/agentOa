package org.dromara.agentoa.reporting.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.reporting.domain.vo.WorkbenchVo;
import org.dromara.agentoa.reporting.service.IReportQueryService;
import org.dromara.common.core.domain.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台接口（API 规范 docs/05 第 10 节）：登录即可读，管理视角卡片在服务内按 rp:report:list 过滤。
 * 审批待办、今日打卡、公告与关键余额均按已验证接口的同一口径读取（docs/18 步骤 5）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/report")
public class ReportDashboardController {

    private final IReportQueryService queryService;

    @GetMapping("/dashboard/workbench")
    public R<WorkbenchVo> workbench() {
        return R.ok(queryService.workbench());
    }
}

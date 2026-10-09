package org.dromara.agentoa.reporting.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.reporting.service.IReportExportService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 导出任务调度（docs/18 步骤 3：大导出异步执行）。
 * 与 OutboxDispatcher 相同的轮询模式；任务领取使用状态条件更新避免重复执行。
 */
@Component
@RequiredArgsConstructor
public class ExportTaskDispatcher {

    private final IReportExportService exportService;

    @Scheduled(fixedDelay = 5000)
    public void poll() {
        exportService.processPending();
    }
}

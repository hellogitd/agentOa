package org.dromara.agentoa.ai.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.enums.AiCopilotScene;
import org.dromara.agentoa.reporting.domain.OaReportExport;
import org.dromara.agentoa.reporting.domain.policy.ReportingAccessPolicy;
import org.dromara.agentoa.reporting.mapper.OaReportExportMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 报表解读只读上下文（docs/21 AI-M4-02）。
 * <p>
 * 指标数值由报表页随请求带入（{@code content}），这里只补一份导出任务的只读元信息摘要；
 * 需具备报表查看权限，避免把管理驾驶舱数据带给无权限账号。
 */
@Component
@RequiredArgsConstructor
public class ReportCopilotReader implements CopilotBizReader {

    private final OaReportExportMapper reportExportMapper;

    @Override
    public String scene() {
        return AiCopilotScene.REPORT_INSIGHT.code();
    }

    @Override
    public String readContext(Long bizId, Long userId) {
        Set<String> permissions = LoginHelper.getLoginUser() == null
            ? Set.of() : LoginHelper.getLoginUser().getMenuPermission();
        if (!ReportingAccessPolicy.canViewDashboards(LoginHelper.isSuperAdmin(), permissions)) {
            throw new ServiceException("AI_COPILOT_BIZ_FORBIDDEN 无权查看报表", 403);
        }
        if (bizId == null) {
            return "";
        }
        OaReportExport export = reportExportMapper.selectById(bizId);
        if (export == null) {
            throw new ServiceException("AI_COPILOT_BIZ_NOT_FOUND 报表任务不存在", 404);
        }
        return "报表类型：" + nullToEmpty(export.getReportType()) + "\n"
            + "导出单号：" + nullToEmpty(export.getExportNo()) + "\n"
            + "筛选条件：" + nullToEmpty(export.getFilters()) + "\n"
            + "数据行数：" + (export.getRowCount() == null ? 0 : export.getRowCount()) + "\n"
            + "状态：" + nullToEmpty(export.getStatus());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

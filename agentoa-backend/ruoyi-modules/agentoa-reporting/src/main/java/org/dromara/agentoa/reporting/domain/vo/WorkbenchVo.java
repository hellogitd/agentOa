package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 工作台数据（docs/05 10 工作台响应 + docs/18 步骤 5 关键余额与管理视角）。
 * 审批待办、今日打卡、公告与关键余额均按已验收接口同一口径读取。
 */
@Data
public class WorkbenchVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long todoCount;

    private Long initiatedCount;

    private List<WorkItemVo> todoRecent;

    private List<WorkItemVo> initiatedRecent;

    private List<WorkEventVo> todayEvents;

    private List<WorkNoticeVo> notices;

    private ReportPunchTodayVo attendance;

    private List<ReportBalanceVo> leaveBalances;

    /** 管理视角卡片（rp:report:list 授权后返回，否则为空）。 */
    private WorkbenchManagementVo management;

    /** 个人视角快捷入口目标（站内路径）。 */
    private List<QuickLinkVo> quickLinks;
}

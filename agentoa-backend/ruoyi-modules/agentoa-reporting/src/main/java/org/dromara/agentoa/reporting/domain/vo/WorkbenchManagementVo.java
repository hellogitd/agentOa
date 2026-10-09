package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 工作台管理视角卡片（需 rp:report:list，docs/02 9.2）。 */
@Data
public class WorkbenchManagementVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 本月部门出勤率（分母为零为空）。 */
    private String deptAttendanceRate;

    /** 本月报销申请额（元）。 */
    private String monthReimburseAmount;

    /** 区间内已结束流程平均处理时长（小时）。 */
    private String avgFlowDurationHours;
}

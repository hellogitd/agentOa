package org.dromara.agentoa.attendance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.time.LocalDate;

/**
 * 考勤组 oa_attendance_group，对应 API 资源 /api/v1/attendance/groups。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_attendance_group")
public class OaAttendanceGroup extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private String groupCode;

    private String groupName;

    private Long shiftId;

    /** 工作日（周一=1），如 1,2,3,4,5 */
    private String workDays;

    /** 规则生效日期 */
    private LocalDate effectiveDate;

    /** 规则配置快照 */
    private String scopeSnapshot;

    /** 0正常 1停用 */
    private String status;

    private String remark;
}

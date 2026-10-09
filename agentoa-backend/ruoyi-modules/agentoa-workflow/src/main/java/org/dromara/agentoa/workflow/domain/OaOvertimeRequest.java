package org.dromara.agentoa.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_overtime_request")
public class OaOvertimeRequest extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private Long userId;

    private Long employeeId;

    private LocalDate overtimeDate;

    /** weekday/weekend/holiday */
    private String overtimeType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationMinutes;

    private String reason;

    /** 1草稿 2审批中 3已通过 4已拒绝 7已撤销 */
    private Integer status;

    private Long flowInstanceId;

    private Integer submissionNo;

    private Integer lockVersion;

    private String remark;
}

package org.dromara.agentoa.attendance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.time.LocalDate;

/**
 * 员工排班指派 oa_shift_assignment（P1，需求 AT-03）。
 * <p>
 * 指派覆盖考勤组默认班次，一人一天最多一条（唯一键）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_shift_assignment")
public class OaShiftAssignment extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private Long userId;

    private Long employeeId;

    private Long shiftId;

    private LocalDate workDate;

    /** 来源（1手工 2轮班） */
    private Integer source;

    private String remark;
}

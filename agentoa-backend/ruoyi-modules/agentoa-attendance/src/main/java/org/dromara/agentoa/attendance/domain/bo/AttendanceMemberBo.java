package org.dromara.agentoa.attendance.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 考勤组成员业务对象。
 */
@Data
public class AttendanceMemberBo {

    private Long id;

    @NotNull(message = "账号不能为空")
    private Long userId;

    private Long employeeId;

    @NotNull(message = "生效日期不能为空")
    private LocalDate validFrom;

    /** 空表示长期 */
    private LocalDate validTo;
}

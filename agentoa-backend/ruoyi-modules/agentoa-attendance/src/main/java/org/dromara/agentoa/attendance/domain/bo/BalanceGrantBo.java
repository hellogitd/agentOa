package org.dromara.agentoa.attendance.domain.bo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 额度发放/调整命令（API 规范 5.3）。
 */
@Data
public class BalanceGrantBo {

    @NotNull(message = "账号不能为空")
    private Long userId;

    private Long employeeId;

    @NotNull(message = "年度不能为空")
    private Integer year;

    @NotBlank(message = "假种不能为空")
    private String leaveType;

    /** 正数发放、负数回收调整 */
    @NotNull(message = "额度分钟不能为空")
    private Integer minutes;

    /** 批次有效起始日（AT-06，默认当天） */
    private LocalDate validFrom;

    /** 批次有效截止日（AT-06；调休默认 +3 个月、年假默认当年 12-31） */
    private LocalDate expireDate;
}

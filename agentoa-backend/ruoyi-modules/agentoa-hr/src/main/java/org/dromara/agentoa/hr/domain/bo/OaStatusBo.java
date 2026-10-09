package org.dromara.agentoa.hr.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 员工状态更新（API 规范 3.3 状态端点）。目标状态决定调用的生命周期命令。
 */
@Data
public class OaStatusBo {

    /** PROBATION / ACTIVE / LEAVE_PENDING / LEFT / DISABLED */
    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "PROBATION|ACTIVE|LEAVE_PENDING|LEFT|DISABLED", message = "状态取值非法")
    private String status;

    @Size(max = 500, message = "原因长度不能超过{max}个字符")
    private String reason;
}

package org.dromara.agentoa.hr.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 离职命令（API 规范 3.7）。离职完成同事务冻结账号并注销会话。
 */
@Data
public class OaOffboardBo {

    @NotNull(message = "员工 ID 不能为空")
    private Long employeeId;

    @Size(max = 500, message = "离职原因长度不能超过{max}个字符")
    private String reason;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastWorkingDay;

    @Size(max = 64, message = "流程实例 ID 长度不能超过{max}个字符")
    private String workflowInstanceId;
}

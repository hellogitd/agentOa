package org.dromara.agentoa.hr.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 转正命令（API 规范 3.5）。P0 直接变更状态，审批链路由流程模块接入。
 */
@Data
public class OaRegularizeBo {

    @NotNull(message = "员工 ID 不能为空")
    private Long employeeId;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate regularDate;

    @Size(max = 64, message = "流程实例 ID 长度不能超过{max}个字符")
    private String workflowInstanceId;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}

package org.dromara.agentoa.workflow.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class LifecycleRequestBo {

    /** 员工档案 ID；为空时按当前登录人解析（员工自服务发起） */
    private Long employeeId;

    @Pattern(regexp = "REGULARIZE|OFFBOARD", message = "申请类型取值非法")
    private String requestType;

    @NotNull(message = "生效日期不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveDate;

    @Size(max = 1000, message = "表单内容长度不能超过{max}个字符")
    private String formData;

    private Integer lockVersion;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}

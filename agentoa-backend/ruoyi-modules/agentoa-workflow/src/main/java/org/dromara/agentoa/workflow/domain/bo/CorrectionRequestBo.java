package org.dromara.agentoa.workflow.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CorrectionRequestBo {

    @NotNull(message = "补卡日期不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate attendanceDate;

    @NotNull(message = "时段不能为空")
    private Integer punchType;

    @NotNull(message = "补卡时间不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime correctedTime;

    @NotBlank(message = "原因不能为空")
    @Size(max = 500, message = "原因长度不能超过{max}个字符")
    private String reason;

    private Integer lockVersion;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}

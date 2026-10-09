package org.dromara.agentoa.workflow.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class OvertimeRequestBo {

    @NotNull(message = "加班日期不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate overtimeDate;

    @Pattern(regexp = "weekday|weekend|holiday", message = "加班类型取值非法")
    private String overtimeType;

    @NotNull(message = "开始时间不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Size(max = 255, message = "事由长度不能超过{max}个字符")
    private String reason;

    private Integer lockVersion;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}

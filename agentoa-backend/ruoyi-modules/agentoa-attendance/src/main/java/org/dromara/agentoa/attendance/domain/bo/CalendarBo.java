package org.dromara.agentoa.attendance.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 工作日历行（API 规范 5.2/13）。
 */
@Data
public class CalendarBo {

    @NotNull(message = "日期不能为空")
    private LocalDate workDate;

    @NotBlank(message = "日期类型不能为空")
    private String dayType;

    @Size(max = 128, message = "说明长度不能超过{max}个字符")
    private String description;
}

package org.dromara.agentoa.attendance.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 节假日登记命令。
 */
@Data
public class HolidayBo {

    private Long id;

    @NotNull(message = "日期不能为空")
    private LocalDate holidayDate;

    @NotBlank(message = "名称不能为空")
    @Size(max = 64, message = "名称长度不能超过{max}个字符")
    private String holidayName;

    /** HOLIDAY 节假日 / MAKEUP 调休上班 */
    @NotBlank(message = "类型不能为空")
    private String holidayType;
}

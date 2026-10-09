package org.dromara.agentoa.attendance.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.agentoa.attendance.domain.OaShift;

import java.time.LocalTime;

/**
 * 班次业务对象（API 规范 5.2）。
 */
@Data
@AutoMapper(target = OaShift.class, reverseConvertGenerate = false)
public class ShiftBo {

    private Long id;

    @NotBlank(message = "班次编码不能为空")
    @Size(max = 64, message = "班次编码长度不能超过{max}个字符")
    private String shiftCode;

    @NotBlank(message = "班次名称不能为空")
    @Size(max = 64, message = "班次名称长度不能超过{max}个字符")
    private String shiftName;

    @NotNull(message = "上班时间不能为空")
    private LocalTime workStartTime;

    @NotNull(message = "下班时间不能为空")
    private LocalTime workEndTime;

    private LocalTime restStartTime;

    private LocalTime restEndTime;

    private Integer isCrossDay = 0;

    private Integer flexibleMinutes = 0;

    private Integer graceMinutes = 0;

    private Integer punchWindowStart = 120;

    private Integer punchWindowEnd = 240;

    private String status = "0";

    private String remark;
}

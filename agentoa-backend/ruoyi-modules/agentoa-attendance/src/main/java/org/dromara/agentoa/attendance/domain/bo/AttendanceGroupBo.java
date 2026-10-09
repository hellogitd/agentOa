package org.dromara.agentoa.attendance.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.agentoa.attendance.domain.OaAttendanceGroup;

import java.time.LocalDate;

/**
 * 考勤组业务对象（API 规范 5.2）。
 */
@Data
@AutoMapper(target = OaAttendanceGroup.class, reverseConvertGenerate = false)
public class AttendanceGroupBo {

    private Long id;

    @NotBlank(message = "考勤组编码不能为空")
    @Size(max = 64, message = "考勤组编码长度不能超过{max}个字符")
    private String groupCode;

    @NotBlank(message = "考勤组名称不能为空")
    @Size(max = 64, message = "考勤组名称长度不能超过{max}个字符")
    private String groupName;

    @NotNull(message = "班次不能为空")
    private Long shiftId;

    @NotBlank(message = "工作日不能为空")
    @Size(max = 32, message = "工作日长度不能超过{max}个字符")
    private String workDays;

    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveDate;

    private String status = "0";

    private String remark;
}

package org.dromara.agentoa.hr.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.agentoa.hr.domain.OaEmployeeEducation;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 教育经历视图对象。
 */
@Data
@AutoMapper(target = OaEmployeeEducation.class)
public class OaEducationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;

    private String school;

    private String major;

    private String education;

    private String degree;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 是否全日制（1是 0否） */
    private Integer isFullTime;

    private String remark;
}

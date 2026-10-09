package org.dromara.agentoa.hr.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.agentoa.hr.domain.OaEmployeeEducation;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDate;

/**
 * 教育经历业务对象（API 规范 3.3 education[]）。
 */
@Data
@AutoMapper(target = OaEmployeeEducation.class, reverseConvertGenerate = false)
public class OaEducationBo extends BaseEntity {

    private Long id;

    private Long employeeId;

    @Size(max = 128, message = "学校长度不能超过{max}个字符")
    private String school;

    @Size(max = 128, message = "专业长度不能超过{max}个字符")
    private String major;

    @Size(max = 32, message = "学历长度不能超过{max}个字符")
    private String education;

    @Size(max = 32, message = "学位长度不能超过{max}个字符")
    private String degree;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 是否全日制（1是 0否） */
    private Integer isFullTime;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}

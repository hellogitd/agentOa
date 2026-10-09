package org.dromara.agentoa.hr.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.agentoa.hr.domain.OaEmployeeWork;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDate;

/**
 * 工作经历业务对象。
 */
@Data
@AutoMapper(target = OaEmployeeWork.class, reverseConvertGenerate = false)
public class OaWorkBo extends BaseEntity {

    private Long id;

    private Long employeeId;

    @Size(max = 128, message = "公司长度不能超过{max}个字符")
    private String company;

    @Size(max = 64, message = "职位长度不能超过{max}个字符")
    private String position;

    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 255, message = "离职原因长度不能超过{max}个字符")
    private String leaveReason;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}

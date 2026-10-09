package org.dromara.agentoa.hr.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.agentoa.hr.domain.OaContract;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDate;

/**
 * 合同业务对象（需求 HR-09）。既做查询条件也做新增/修改入参。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = OaContract.class, reverseConvertGenerate = false)
public class OaContractBo extends BaseEntity {

    private Long id;

    private Long employeeId;

    @Size(max = 64, message = "合同编号长度不能超过{max}个字符")
    private String contractNo;

    @NotBlank(message = "合同类型不能为空")
    @Pattern(regexp = "FIXED_TERM|OPEN_ENDED|TASK_BASED", message = "合同类型取值非法")
    private String contractType;

    @NotNull(message = "合同开始日期不能为空")
    private LocalDate startDate;

    private LocalDate endDate;

    private LocalDate signDate;

    private Integer renewCount;

    /** 状态（1生效 2到期 3终止）；服务端校验取值 */
    private Integer status;

    private Long fileId;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;
}

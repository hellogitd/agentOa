package org.dromara.agentoa.hr.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工异动命令（API 规范 3.6 POST /api/v1/hr/changes）。
 * <p>
 * 原部门/岗位/职级/薪资由服务端从档案快照，客户端只提交目标值。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OaChangeBo extends BaseEntity {

    @NotNull(message = "员工不能为空")
    private Long employeeId;

    /** 异动类型（3调岗 4调薪 5晋升 6降级）；服务端校验取值 */
    @NotNull(message = "异动类型不能为空")
    private Integer changeType;

    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveDate;

    private Long newDeptId;

    private Long newPostId;

    @Size(max = 32, message = "职级长度不能超过{max}个字符")
    private String newPositionLevel;

    /** 新薪资（明文金额，服务端加密存储） */
    @DecimalMin(value = "0.00", message = "薪资不能为负数")
    private BigDecimal newSalary;

    @Size(max = 255, message = "原因长度不能超过{max}个字符")
    private String reason;

    /** 关联流程实例 ID，可空 */
    @Size(max = 64, message = "流程实例ID长度不能超过{max}个字符")
    private String flowInstanceId;

    /** 来源申请 ID；提供时作为一次生效去重键 */
    private Long sourceRequestId;

    /** 查询条件：是否已生效（1是 0否） */
    private Integer applied;
}

package org.dromara.agentoa.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_reimburse_request")
public class OaReimburseRequest extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private String reimburseNo;

    private Long userId;

    private Long employeeId;

    private String reimburseType;

    /** 服务端按明细汇总 */
    private BigDecimal totalAmount;

    private String currency;

    private String payMethod;

    private String detailsJson;

    /** 关联预算 ID（P1，FN-03 预算控制） */
    private Long budgetId;

    /** 已付金额（部分付款累计） */
    private BigDecimal paidAmount;

    private String reason;

    /** 1草稿 2审批中 3已通过 4已拒绝 5待付款 6已付款 7已撤销 */
    private Integer status;

    private Long flowInstanceId;

    private Integer submissionNo;

    private Integer lockVersion;

    private String remark;
}

package org.dromara.agentoa.finance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 预算 oa_budget（P1，需求 FN-03）。可用 = total - used - frozen。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_budget")
public class OaBudget extends TenantEntity {

    /** 部门预算 */
    public static final int TYPE_DEPT = 1;
    /** 项目预算 */
    public static final int TYPE_PROJECT = 2;

    /** 执行中 */
    public static final int STATUS_ACTIVE = 1;
    /** 已关闭 */
    public static final int STATUS_CLOSED = 2;

    @TableId(value = "id")
    private Long id;

    private String budgetCode;

    private String budgetName;

    /** 类型（1部门 2项目） */
    private Integer budgetType;

    /** 归属 ID（部门 ID / 项目 ID） */
    private Long ownerId;

    private Integer year;

    /** 季度（1-4，空=年度） */
    private Integer quarter;

    private BigDecimal totalAmount;

    private BigDecimal usedAmount;

    private BigDecimal frozenAmount;

    /** 预警阈值（%） */
    private Integer warnThreshold;

    /** 状态（1执行 2关闭） */
    private Integer status;

    /** 乐观锁版本 */
    private Integer lockVersion;

    private String remark;
}

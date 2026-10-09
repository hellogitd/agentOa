package org.dromara.agentoa.finance.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 预算流水 oa_budget_ledger：提交冻结、通过转已用、拒绝/撤销释放（event_key 幂等）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_budget_ledger")
public class OaBudgetLedger extends TenantEntity {

    /** 冻结 */
    public static final String ACTION_OCCUPY = "OCCUPY";
    /** 转已用 */
    public static final String ACTION_SETTLE = "SETTLE";
    /** 释放 */
    public static final String ACTION_RELEASE = "RELEASE";
    /** 调整 */
    public static final String ACTION_ADJUST = "ADJUST";

    @TableId(value = "id")
    private Long id;

    private Long budgetId;

    /** 业务事件 ID（幂等） */
    private String eventKey;

    /** 动作（OCCUPY/SETTLE/RELEASE/ADJUST） */
    private String action;

    private BigDecimal amount;

    private String bizType;

    private Long bizId;

    private Long operatorId;

    private Date createTime;
}

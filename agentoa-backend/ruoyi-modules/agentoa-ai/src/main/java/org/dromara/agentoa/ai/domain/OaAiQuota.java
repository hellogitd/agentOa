package org.dromara.agentoa.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * AI 用量配额 oa_ai_quota（docs/21 AI-M1-08）：主体（用户/角色）+ 周期（日/月）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_ai_quota")
public class OaAiQuota extends TenantEntity {

    public static final String SCOPE_USER = "user";
    public static final String SCOPE_ROLE = "role";
    public static final String PERIOD_DAY = "day";
    public static final String PERIOD_MONTH = "month";

    @TableId(value = "id")
    private Long id;

    /** user/role */
    private String scopeType;

    private Long scopeId;

    private String scopeName;

    /** day/month */
    private String periodType;

    /** token 限额，NULL 不限 */
    private Long tokenLimit;

    /** 请求次数限额，NULL 不限 */
    private Integer requestLimit;

    /** 1 启用 0 停用 */
    private Integer enabled;

    private String remark;
}

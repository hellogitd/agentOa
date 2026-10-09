package org.dromara.agentoa.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * AI 模型 oa_ai_model（docs/21 AI-M1-04）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_ai_model")
public class OaAiModel extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private Long providerId;

    /** 上游模型标识 */
    private String modelKey;

    private String alias;

    /** 能力 JSON 数组：chat/vision/embedding/rerank */
    private String capability;

    /** 上下文窗口（token） */
    private Integer contextWindow;

    private BigDecimal defaultTemperature;

    private Integer maxTokens;

    /** 1 启用 0 停用 */
    private Integer enabled;

    /** 全局默认模型 */
    private Integer isDefault;

    private String remark;
}

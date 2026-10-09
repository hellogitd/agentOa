package org.dromara.agentoa.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * AI Agent oa_ai_agent（docs/21 AI-M5-03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_ai_agent")
public class OaAiAgent extends TenantEntity {

    public static final String STATUS_ENABLED = "1";

    @TableId(value = "id")
    private Long id;

    private String code;

    private String name;

    private String systemPrompt;

    /** 默认模型 ID（空取全局默认） */
    private Long modelId;

    /** 可用工具码 JSON 数组 */
    private String toolCodes;

    /** 最大执行步数（默认 6） */
    private Integer maxSteps;

    /** 单次运行超时（秒） */
    private Integer timeoutSec;

    /** 1 启用 0 停用 */
    private Integer enabled;

    /** 1 内置 0 自定义 */
    private Integer isBuiltin;

    private String remark;
}

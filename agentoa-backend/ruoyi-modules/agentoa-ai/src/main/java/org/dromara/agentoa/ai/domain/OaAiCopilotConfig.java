package org.dromara.agentoa.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * AI 业务助手场景配置 oa_ai_copilot_config（docs/21 AI-M4-01~05）。
 * <p>
 * 每场景独立开关 + 模型 + 提示词模板；AI 只读业务摘要并给出草稿，不直接写业务数据。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_ai_copilot_config")
public class OaAiCopilotConfig extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    /** 场景码，见 {@code AiCopilotScene} */
    private String sceneCode;

    private String sceneName;

    /** 1 启用 0 停用 */
    private Integer enabled;

    /** 生成模型 ID（空取全局默认模型） */
    private Long modelId;

    /** 提示词模板 ID */
    private Long promptTemplateId;

    private String remark;
}

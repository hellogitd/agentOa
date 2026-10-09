package org.dromara.agentoa.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * AI 提示词模板 oa_ai_prompt_template（docs/21 AI-M2-05）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_ai_prompt_template")
public class OaAiPromptTemplate extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private String code;

    private String name;

    private String category;

    private String content;

    /** 1 启用 0 停用 */
    private Integer enabled;

    /** 1 内置 0 自定义 */
    private Integer isBuiltin;

    private String remark;
}

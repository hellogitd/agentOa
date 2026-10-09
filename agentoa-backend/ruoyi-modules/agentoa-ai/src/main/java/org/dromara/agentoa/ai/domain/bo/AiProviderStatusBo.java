package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 渠道启停（docs/21 AI-M1-01）。
 */
@Data
public class AiProviderStatusBo {

    @NotNull(message = "启用状态不能为空")
    private Integer enabled;
}

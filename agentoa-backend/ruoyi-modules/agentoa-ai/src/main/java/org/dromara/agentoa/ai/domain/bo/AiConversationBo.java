package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 会话创建/更新（docs/21 AI-M2-01）。
 */
@Data
public class AiConversationBo {

    private Long id;

    @Size(max = 255, message = "标题最长 255 字符")
    private String title;

    /** 绑定模型（可切换，历史消息不重算） */
    private Long modelId;

    /** 绑定提示词模板 */
    private Long promptTemplateId;
}

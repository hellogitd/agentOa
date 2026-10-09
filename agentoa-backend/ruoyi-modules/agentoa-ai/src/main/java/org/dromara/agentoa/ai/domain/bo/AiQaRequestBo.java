package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 知识问答请求（docs/21 §5.3 POST /qa/ask）。
 */
@Data
public class AiQaRequestBo {

    /** 知识域 ID 列表，空则取本人全部可见知识域 */
    @Size(max = 20, message = "知识域最多{max}个")
    private List<Long> kbIds;

    @NotBlank(message = "问题不能为空")
    @Size(max = 20000, message = "问题最长{max}个字符")
    private String query;

    /** 续问会话（qa 场景），空则新建 */
    private Long conversationId;

    /** 回答模型，空则用知识域/全局默认模型 */
    private Long modelId;
}

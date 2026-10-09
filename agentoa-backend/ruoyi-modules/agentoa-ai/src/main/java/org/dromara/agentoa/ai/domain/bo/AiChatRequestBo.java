package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 对话补全请求（docs/21 AI-M2-03/04）。
 */
@Data
public class AiChatRequestBo {

    /** 空则创建新会话 */
    private Long conversationId;

    /** 空则用会话绑定模型或全局默认模型 */
    private Long modelId;

    /** 空则用会话绑定模板 */
    private Long promptTemplateId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 20000, message = "消息内容最长 20000 字符")
    private String content;

    /** 图片附件（sys_file id），≤5 张，仅 vision 模型可用 */
    @Size(max = 5, message = "图片附件最多 5 张")
    private List<Long> attachmentIds;
}

package org.dromara.agentoa.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * AI 对话消息 oa_ai_message（docs/21 AI-M2-02）：持久化即记忆来源。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_ai_message")
public class OaAiMessage extends TenantEntity {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";

    public static final String STATUS_STREAMING = "streaming";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_STOPPED = "stopped";
    public static final String STATUS_ERROR = "error";

    @TableId(value = "id")
    private Long id;

    private Long conversationId;

    /** user/assistant/system */
    private String role;

    private String content;

    /** 附件 JSON：sys_file id 列表 */
    private String attachments;

    /** 知识问答引用 JSON 数组（docs/21 M3） */
    private String citations;

    private Long modelId;

    private Integer promptTokens;

    private Integer completionTokens;

    /** streaming/done/stopped/error */
    private String status;

    private String errorCode;

    private String remark;
}

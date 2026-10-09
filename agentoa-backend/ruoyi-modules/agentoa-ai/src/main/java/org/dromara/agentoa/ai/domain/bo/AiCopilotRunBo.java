package org.dromara.agentoa.ai.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 业务助手生成请求（docs/21 §6.3 POST /copilot/{scene}）。
 */
@Data
public class AiCopilotRunBo {

    /** 场景码（路径变量亦可覆盖） */
    @Pattern(regexp = "approve-summary|report-insight|notice-draft|form-suggest|minutes",
        message = "场景码不在字典范围内")
    private String scene;

    /** 业务 ID（可选；缺省时按 content 直接生成） */
    private Long bizId;

    /** 业务类型（缺省按场景推断） */
    @Size(max = 32, message = "业务类型最长 {max} 字符")
    private String bizType;

    /** 用户意图/补充说明 */
    @Size(max = 4000, message = "补充说明最长 {max} 字符")
    private String instruction;

    /** 直接提供的业务摘要（前端可传字段摘要，服务端亦会尝试只读补充） */
    @Size(max = 20000, message = "业务摘要最长 {max} 字符")
    private String content;

    /** 是否异步（长摘要/报表生成建议异步） */
    private Boolean async;

    /** 覆盖模型 ID（可选，需管理员） */
    private Long modelId;
}

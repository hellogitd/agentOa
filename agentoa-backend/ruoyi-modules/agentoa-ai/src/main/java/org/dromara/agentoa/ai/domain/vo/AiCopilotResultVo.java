package org.dromara.agentoa.ai.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * 业务助手生成结果（docs/21 §6.3）。
 * <p>
 * AI 生成内容仅供参考，须人工确认后才进入业务流程。
 */
@Data
public class AiCopilotResultVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    private String scene;

    private String status;

    /** 生成正文（AI 生成内容仅供参考） */
    private String content;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    /** 引用的业务只读摘要来源（bizType/bizId/title） */
    private List<String> sources;
}

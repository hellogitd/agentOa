package org.dromara.agentoa.ai.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * 知识问答结果（docs/21 AI-M3-05）：回答 + 引用列表。
 */
@Data
public class QaResultVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageId;

    private String content;

    private Integer promptTokens;

    private Integer completionTokens;

    private String status;

    private List<QaCitationVo> citations;
}

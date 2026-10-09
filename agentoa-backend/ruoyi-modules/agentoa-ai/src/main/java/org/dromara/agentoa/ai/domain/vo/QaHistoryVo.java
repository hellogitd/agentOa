package org.dromara.agentoa.ai.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 知识问答历史（docs/21 §5.3 GET /qa/history）：一问一答 + 引用。
 */
@Data
public class QaHistoryVo {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageId;

    private String question;

    private String answer;

    private List<QaCitationVo> citations;

    private Date createTime;
}

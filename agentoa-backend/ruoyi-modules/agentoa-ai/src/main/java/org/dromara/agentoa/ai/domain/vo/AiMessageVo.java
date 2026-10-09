package org.dromara.agentoa.ai.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 消息视图。
 */
@Data
public class AiMessageVo {

    private Long id;

    private Long conversationId;

    private String role;

    private String content;

    /** 附件 file id 列表 */
    private List<Long> attachments = new ArrayList<>();

    private Long modelId;

    private Integer promptTokens;

    private Integer completionTokens;

    private String status;

    private String errorCode;

    private Date createTime;
}

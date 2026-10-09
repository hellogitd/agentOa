package org.dromara.agentoa.ai.domain.vo;

import lombok.Data;

import java.util.Date;

/**
 * 会话视图。
 */
@Data
public class AiConversationVo {

    private Long id;

    private Long userId;

    private String title;

    private Long modelId;

    private Long promptTemplateId;

    private String status;

    private Date lastMessageTime;

    private Date createTime;
}

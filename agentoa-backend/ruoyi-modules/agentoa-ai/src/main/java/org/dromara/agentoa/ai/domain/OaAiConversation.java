package org.dromara.agentoa.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * AI 对话会话 oa_ai_conversation（docs/21 AI-M2-01）：本人可见，软删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_ai_conversation")
public class OaAiConversation extends TenantEntity {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DELETED = "deleted";

    /** 场景：普通对话 */
    public static final String SCENE_CHAT = "chat";
    /** 场景：知识问答（docs/21 M3） */
    public static final String SCENE_QA = "qa";

    @TableId(value = "id")
    private Long id;

    private Long userId;

    private String title;

    private Long modelId;

    private Long promptTemplateId;

    /** active/deleted */
    private String status;

    /** 场景（chat/qa） */
    private String scene;

    private Date lastMessageTime;

    private String remark;
}

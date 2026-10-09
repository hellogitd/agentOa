package org.dromara.agentoa.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * AI 知识域 oa_ai_kb（docs/21 AI-M3-01）：RAG 检索单元，可见性服务端过滤。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_ai_kb")
public class OaAiKb extends TenantEntity {

    /** 可见性：仅创建者与显式成员 */
    public static final String VISIBILITY_PRIVATE = "private";
    /** 可见性：创建者与成员 */
    public static final String VISIBILITY_MEMBERS = "members";
    /** 可见性：全员 */
    public static final String VISIBILITY_ALL = "all";

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";

    @TableId(value = "id")
    private Long id;

    private String name;

    private String description;

    /** private/members/all */
    private String visibility;

    /** 成员授权 JSON：账号 ID 数组 */
    private String memberScope;

    /** 向量模型 ID（capability 需含 embedding） */
    private Long embeddingModelId;

    /** active/disabled */
    private String status;

    private String remark;
}

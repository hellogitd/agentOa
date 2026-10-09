package org.dromara.agentoa.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * AI 业务助手异步任务 oa_ai_copilot_task（docs/21 AI-M4-06）。
 * <p>
 * 长摘要/报表类生成走异步任务，可删除（outbox）、可重试；失败原因只留安全摘要。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_ai_copilot_task")
public class OaAiCopilotTask extends TenantEntity {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_FAILED = "failed";

    @TableId(value = "id")
    private Long id;

    /** 场景码，见 {@code AiCopilotScene} */
    private String sceneCode;

    /** 业务类型（如 flow_instance/notice/calendar_event） */
    private String bizType;

    /** 业务 ID */
    private Long bizId;

    /** 发起账号 ID（仅本人可见） */
    private Long userId;

    /** 输入引用 JSON（只读摘要来源） */
    private String inputRef;

    /** 生成结果（AI 生成内容仅供参考） */
    private String output;

    /** pending/running/done/failed */
    private String status;

    private String errorMsg;

    private Integer promptTokens;

    private Integer totalTokens;

    /** 完成时间 */
    private Date finishTime;

    private String remark;
}

package org.dromara.agentoa.ai.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * AI Agent 运行记录 oa_ai_agent_run（docs/21 AI-M5-04）。
 * <p>
 * {@code trace_json} 记录多步执行轨迹（工具调用/参数/结果摘要），支持回放观察。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_ai_agent_run")
public class OaAiAgentRun extends TenantEntity {

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_STOPPED = "stopped";

    @TableId(value = "id")
    private Long id;

    private Long agentId;

    private Long userId;

    /** 关联对话 ID（Agent 模式复用会话） */
    private Long conversationId;

    private String input;

    /** 执行轨迹 JSON */
    private String traceJson;

    /** 最终输出（AI 生成内容仅供参考） */
    private String output;

    /** running/done/failed/stopped */
    private String status;

    private String errorMsg;

    private Integer totalTokens;

    private Integer durationMs;

    private String remark;
}

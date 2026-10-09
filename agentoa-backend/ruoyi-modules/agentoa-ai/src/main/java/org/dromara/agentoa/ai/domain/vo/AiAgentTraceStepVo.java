package org.dromara.agentoa.ai.domain.vo;

import lombok.Data;

/**
 * Agent 执行轨迹一步（docs/21 AI-M5-04：可观察执行轨迹）。
 */
@Data
public class AiAgentTraceStepVo {

    /** 第几步（1 起） */
    private Integer step;

    /** thought / tool_call / tool_result / final */
    private String type;

    /** 工具码（tool_call/tool_result） */
    private String tool;

    /** 工具入参 JSON */
    private String arguments;

    /** 工具结果摘要（脱敏、长度受限） */
    private String result;

    /** 是否写类工具（需人工确认） */
    private Boolean writeTool;

    /** 本步耗时（毫秒） */
    private Long durationMs;
}

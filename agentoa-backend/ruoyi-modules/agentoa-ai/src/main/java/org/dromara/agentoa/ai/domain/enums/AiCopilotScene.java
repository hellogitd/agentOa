package org.dromara.agentoa.ai.domain.enums;

import org.dromara.common.core.exception.ServiceException;

/**
 * AI 业务助手场景（docs/21 §6.1）。
 * <p>
 * 场景码与 {@code oa_ai_copilot_config.scene_code} 一致；新增场景需同时补充提示词模板与权限。
 */
public enum AiCopilotScene {

    /** 审批摘要（AI-M4-01） */
    APPROVE_SUMMARY("approve-summary", "审批摘要", "flow_instance"),
    /** 报表解读（AI-M4-02） */
    REPORT_INSIGHT("report-insight", "报表解读", "report"),
    /** 通知/公文起草（AI-M4-03） */
    NOTICE_DRAFT("notice-draft", "通知起草", "notice"),
    /** 表单填写建议（AI-M4-04） */
    FORM_SUGGEST("form-suggest", "表单填写建议", "flow_form"),
    /** 会议纪要（AI-M4-05） */
    MINUTES("minutes", "会议纪要", "calendar_event");

    private final String code;
    private final String label;
    private final String bizType;

    AiCopilotScene(String code, String label, String bizType) {
        this.code = code;
        this.label = label;
        this.bizType = bizType;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** 该场景默认的业务类型（oa_ai_copilot_task.biz_type） */
    public String bizType() {
        return bizType;
    }

    /** 解析场景码，未知码返回 400 */
    public static AiCopilotScene require(String code) {
        if (code != null) {
            for (AiCopilotScene scene : values()) {
                if (scene.code.equals(code)) {
                    return scene;
                }
            }
        }
        throw new ServiceException("AI_COPILOT_SCENE_UNKNOWN 不支持的业务助手场景", 400);
    }

    public static boolean known(String code) {
        if (code == null) {
            return false;
        }
        for (AiCopilotScene scene : values()) {
            if (scene.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}

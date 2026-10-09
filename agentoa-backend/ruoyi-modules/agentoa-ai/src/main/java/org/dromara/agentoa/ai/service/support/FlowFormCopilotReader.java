package org.dromara.agentoa.ai.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.enums.AiCopilotScene;
import org.dromara.agentoa.workflow.domain.OaFlowFormVersion;
import org.dromara.agentoa.workflow.mapper.OaFlowFormVersionMapper;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

/**
 * 表单填写建议只读上下文（docs/21 AI-M4-04）。
 * <p>
 * 只读取表单模板字段定义（属模板而非业务明细），供模型给出填写建议；不回写、不提交。
 */
@Component
@RequiredArgsConstructor
public class FlowFormCopilotReader implements CopilotBizReader {

    /** 单次注入提示词的表单定义上限 */
    private static final int MAX_SCHEMA_CHARS = 4000;

    private final OaFlowFormVersionMapper formVersionMapper;

    @Override
    public String scene() {
        return AiCopilotScene.FORM_SUGGEST.code();
    }

    @Override
    public String readContext(Long bizId, Long userId) {
        if (bizId == null) {
            return "";
        }
        OaFlowFormVersion version = formVersionMapper.selectById(bizId);
        if (version == null) {
            throw new ServiceException("AI_COPILOT_BIZ_NOT_FOUND 表单版本不存在", 404);
        }
        String schema = version.getSchemaJson();
        if (schema != null && schema.length() > MAX_SCHEMA_CHARS) {
            schema = schema.substring(0, MAX_SCHEMA_CHARS) + "…";
        }
        return "表单名称：" + (version.getFormName() == null ? "" : version.getFormName()) + "\n"
            + "表单版本：v" + version.getVersionNo() + "\n"
            + "字段定义（JSON Schema）：" + (schema == null ? "（空）" : schema);
    }
}

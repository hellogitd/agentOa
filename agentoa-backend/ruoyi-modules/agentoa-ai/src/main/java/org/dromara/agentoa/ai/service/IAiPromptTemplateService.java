package org.dromara.agentoa.ai.service;

import org.dromara.agentoa.ai.domain.OaAiPromptTemplate;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiPromptTemplateBo;
import org.dromara.agentoa.ai.domain.vo.AiPromptTemplateVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.List;

/**
 * 提示词模板服务（docs/21 AI-M2-05）。
 */
public interface IAiPromptTemplateService {

    PageVo<AiPromptTemplateVo> page(AiPageQuery query);

    /** 启用模板（登录即可） */
    List<AiPromptTemplateVo> listEnabled();

    AiPromptTemplateVo create(AiPromptTemplateBo bo);

    AiPromptTemplateVo update(AiPromptTemplateBo bo);

    void delete(Long id);

    OaAiPromptTemplate require(Long id);
}

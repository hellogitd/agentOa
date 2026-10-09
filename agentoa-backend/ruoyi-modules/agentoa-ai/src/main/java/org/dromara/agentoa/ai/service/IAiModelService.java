package org.dromara.agentoa.ai.service;

import org.dromara.agentoa.ai.domain.bo.AiModelBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.vo.AiModelVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.List;

/**
 * 模型服务（docs/21 AI-M1-04）。
 */
public interface IAiModelService {

    PageVo<AiModelVo> page(AiModelBo query, AiPageQuery page);

    AiModelVo get(Long id);

    /** 启用模型（登录即可，供选择器） */
    List<AiModelVo> listEnabled();

    AiModelVo create(AiModelBo bo);

    AiModelVo update(AiModelBo bo);

    void delete(Long id);
}

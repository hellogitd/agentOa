package org.dromara.agentoa.ai.service;

import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiProviderBo;
import org.dromara.agentoa.ai.domain.bo.AiProviderTestBo;
import org.dromara.agentoa.ai.domain.vo.AiProviderTestVo;
import org.dromara.agentoa.ai.domain.vo.AiProviderVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

/**
 * 模型渠道服务（docs/21 AI-M1-01/02/03）。
 */
public interface IAiProviderService {

    PageVo<AiProviderVo> page(AiPageQuery query);

    AiProviderVo get(Long id);

    AiProviderVo create(AiProviderBo bo);

    AiProviderVo update(AiProviderBo bo);

    void delete(Long id);

    void updateStatus(Long id, Integer enabled);

    AiProviderTestVo test(Long id, AiProviderTestBo bo);
}

package org.dromara.agentoa.ai.service;

import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiQuotaBo;
import org.dromara.agentoa.ai.domain.vo.AiQuotaVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

/**
 * 用量配额服务（docs/21 AI-M1-08）。
 */
public interface IAiQuotaService {

    PageVo<AiQuotaVo> page(AiPageQuery query);

    AiQuotaVo create(AiQuotaBo bo);

    AiQuotaVo update(AiQuotaBo bo);

    void delete(Long id);
}

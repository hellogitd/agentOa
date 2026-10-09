package org.dromara.agentoa.ai.service;

import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiUsageQueryBo;
import org.dromara.agentoa.ai.domain.vo.AiUsageLogVo;
import org.dromara.agentoa.ai.domain.vo.AiUsageStatVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 用量统计服务（docs/21 AI-M1-09）。
 */
public interface IAiUsageService {

    AiUsageStatVo stats(AiUsageQueryBo query);

    PageVo<AiUsageLogVo> logs(AiUsageQueryBo query, AiPageQuery page);

    void export(AiUsageQueryBo query, HttpServletResponse response);
}

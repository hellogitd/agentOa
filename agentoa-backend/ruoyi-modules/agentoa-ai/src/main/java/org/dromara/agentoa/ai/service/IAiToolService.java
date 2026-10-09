package org.dromara.agentoa.ai.service;

import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiToolBo;
import org.dromara.agentoa.ai.domain.vo.AiToolDiscoveryVo;
import org.dromara.agentoa.ai.domain.vo.AiToolTestVo;
import org.dromara.agentoa.ai.domain.vo.AiToolVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.List;

/**
 * 工具与 MCP 管理（docs/21 M5）。
 */
public interface IAiToolService {

    PageVo<AiToolVo> page(AiPageQuery page);

    AiToolVo get(Long id);

    AiToolVo create(AiToolBo bo);

    AiToolVo update(AiToolBo bo);

    void delete(Long id);

    /** 连通性测试（function 看实现是否就绪，mcp 尝试连接并列出工具） */
    AiToolTestVo test(Long id);

    /** 发现外部 MCP server 上的工具列表（不落库） */
    List<AiToolDiscoveryVo> discover(Long id);

    /** 把外部 MCP server 上发现的工具纳入工具清单（docs/21 AI-M5-02） */
    List<AiToolVo> importDiscovered(Long id);
}

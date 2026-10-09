package org.dromara.agentoa.ai.service;

import org.dromara.agentoa.ai.domain.bo.AiAgentBo;
import org.dromara.agentoa.ai.domain.bo.AiAgentRunBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.vo.AiAgentRunVo;
import org.dromara.agentoa.ai.domain.vo.AiAgentTraceStepVo;
import org.dromara.agentoa.ai.domain.vo.AiAgentVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;

import java.util.List;

/**
 * Agent 配置与运行（docs/21 M5）。
 */
public interface IAiAgentService {

    /** 运行回调（错误统一为 ServiceException） */
    interface RunListener {
        void onStep(AiAgentTraceStepVo step);

        void onDelta(String delta);

        void onComplete(AiAgentRunVo run);

        void onError(ServiceException error);
    }

    /** 运行句柄 */
    interface RunHandle {
        void cancel();

        Long runId();
    }

    PageVo<AiAgentVo> page(AiPageQuery page);

    AiAgentVo get(Long id);

    AiAgentVo create(AiAgentBo bo);

    AiAgentVo update(AiAgentBo bo);

    void delete(Long id);

    /** 发起运行（SSE 由控制器适配） */
    RunHandle run(Long agentId, AiAgentRunBo bo, Long userId, String username, RunListener listener);

    /** 运行记录（本人可见；管理员可看全部） */
    PageVo<AiAgentRunVo> runs(Long agentId, AiPageQuery page, Long userId);

    /** 运行详情（含执行轨迹） */
    AiAgentRunVo runDetail(Long runId, Long userId);
}

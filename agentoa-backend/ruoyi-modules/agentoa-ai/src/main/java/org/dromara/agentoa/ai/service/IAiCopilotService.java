package org.dromara.agentoa.ai.service;

import org.dromara.agentoa.ai.domain.bo.AiCopilotRunBo;
import org.dromara.agentoa.ai.domain.bo.AiCopilotSceneBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.vo.AiCopilotResultVo;
import org.dromara.agentoa.ai.domain.vo.AiCopilotSceneVo;
import org.dromara.agentoa.ai.domain.vo.AiCopilotTaskVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;

import java.util.List;

/**
 * 业务助手（docs/21 M4）：场景配置、生成与异步任务。
 * <p>
 * AI 只读业务摘要并产出草稿，人工确认后才进入业务流程。
 */
public interface IAiCopilotService {

    /** 流式回调（错误统一为 ServiceException） */
    interface StreamListener {
        void onDelta(String delta);

        void onComplete(AiCopilotResultVo result);

        void onError(ServiceException error);
    }

    /** 流式句柄 */
    interface RunHandle {
        void cancel();

        Long taskId();
    }

    /** 场景清单（缺配置时按内置场景补齐，等价 bootstrap 录入） */
    List<AiCopilotSceneVo> scenes(Long userId);

    /** 管理员配置：启停 / 换模型 / 换模板 */
    AiCopilotSceneVo updateScene(String code, AiCopilotSceneBo bo, Long userId);

    /** 同步 SSE 生成（docs/21 §6.3 POST /copilot/{scene}） */
    RunHandle run(AiCopilotRunBo bo, Long userId, String username, StreamListener listener);

    PageVo<AiCopilotTaskVo> tasks(AiPageQuery page, Long userId);

    AiCopilotTaskVo task(Long id, Long userId);

    /** 异步重试（AI-M4-06）：立即返回任务，后台生成 */
    AiCopilotTaskVo retry(Long taskId, Long userId, String username);

    void deleteTask(Long id, Long userId);
}

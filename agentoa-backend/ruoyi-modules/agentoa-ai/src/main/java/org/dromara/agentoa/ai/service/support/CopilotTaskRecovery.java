package org.dromara.agentoa.ai.service.support;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiCopilotTask;
import org.dromara.agentoa.ai.mapper.OaAiCopilotTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 业务助手孤儿任务回收（docs/21 M4 最小补强）。
 * <p>
 * 同步 SSE 主路径与异步重试执行池均为进程内执行，进程中断会留下 pending/running
 * 任务且无人继续执行：应用启动时统一置 failed（可经「重新生成」重试），
 * 避免任务状态机永久卡死。与 {@code AiCopilotServiceImpl.RETRY_EXECUTOR}
 * 同为单节点部署口径。
 */
@Component
@RequiredArgsConstructor
public class CopilotTaskRecovery implements ApplicationRunner {

    /** 回收原因（安全摘要，不落上游错误） */
    public static final String RECOVERED_MSG = "进程中断回收，可重新生成";

    private static final Logger log = LoggerFactory.getLogger(CopilotTaskRecovery.class);

    private final OaAiCopilotTaskMapper taskMapper;

    @Override
    public void run(ApplicationArguments args) {
        int recovered = recoverOrphans();
        if (recovered > 0) {
            log.info("AI Copilot 孤儿任务回收 {} 条（pending/running -> failed）", recovered);
        }
    }

    /**
     * 回收孤儿任务：pending/running 在进程重启后无人继续，置 failed 允许重试。
     *
     * @return 回收条数
     */
    public int recoverOrphans() {
        return taskMapper.update(null, new LambdaUpdateWrapper<OaAiCopilotTask>()
            .in(OaAiCopilotTask::getStatus, OaAiCopilotTask.STATUS_PENDING, OaAiCopilotTask.STATUS_RUNNING)
            .set(OaAiCopilotTask::getStatus, OaAiCopilotTask.STATUS_FAILED)
            .set(OaAiCopilotTask::getErrorMsg, RECOVERED_MSG)
            .set(OaAiCopilotTask::getFinishTime, new Date()));
    }
}

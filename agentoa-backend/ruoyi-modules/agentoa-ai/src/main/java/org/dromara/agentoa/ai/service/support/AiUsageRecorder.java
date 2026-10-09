package org.dromara.agentoa.ai.service.support;

import org.dromara.agentoa.ai.domain.OaAiUsageLog;
import org.dromara.agentoa.ai.mapper.OaAiUsageLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 用量日志异步落库（docs/21 AI-M1-07）：不阻塞主流程；队列满时降级为调用线程写入。
 */
@Component
public class AiUsageRecorder {

    private final OaAiUsageLogMapper usageLogMapper;
    private final ThreadPoolExecutor executor;

    @Autowired
    public AiUsageRecorder(OaAiUsageLogMapper usageLogMapper) {
        this.usageLogMapper = usageLogMapper;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(1000), runnable -> {
                Thread thread = new Thread(runnable, "ai-usage-log");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public void record(OaAiUsageLog log) {
        if (log == null) {
            return;
        }
        if (log.getCreateTime() == null) {
            log.setCreateTime(new Date());
        }
        executor.execute(() -> {
            try {
                usageLogMapper.insert(log);
            } catch (RuntimeException ignored) {
                // 用量日志不得影响主流程
            }
        });
    }

    /** 等待队列写完（测试与优雅停机用） */
    public void flush() {
        executor.execute(() -> {
        });
        try {
            while (executor.getQueue().size() > 0 || executor.getActiveCount() > 0) {
                Thread.sleep(10L);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

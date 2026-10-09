package org.dromara.agentoa.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiCopilotConfig;
import org.dromara.agentoa.ai.domain.OaAiCopilotTask;
import org.dromara.agentoa.ai.domain.OaAiModel;
import org.dromara.agentoa.ai.domain.OaAiPromptTemplate;
import org.dromara.agentoa.ai.domain.bo.AiCopilotRunBo;
import org.dromara.agentoa.ai.domain.bo.AiCopilotSceneBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.enums.AiBizType;
import org.dromara.agentoa.ai.domain.enums.AiCopilotScene;
import org.dromara.agentoa.ai.domain.vo.AiCopilotResultVo;
import org.dromara.agentoa.ai.domain.vo.AiCopilotSceneVo;
import org.dromara.agentoa.ai.domain.vo.AiCopilotTaskVo;
import org.dromara.agentoa.ai.mapper.OaAiCopilotConfigMapper;
import org.dromara.agentoa.ai.mapper.OaAiCopilotTaskMapper;
import org.dromara.agentoa.ai.mapper.OaAiModelMapper;
import org.dromara.agentoa.ai.mapper.OaAiPromptTemplateMapper;
import org.dromara.agentoa.ai.service.IAiCopilotService;
import org.dromara.agentoa.ai.service.support.CopilotBizReader;
import org.dromara.agentoa.ai.service.support.CopilotNotifier;
import org.dromara.agentoa.ai.service.support.ChatTransport;
import org.dromara.agentoa.ai.service.support.LlmGateway;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务助手实现（docs/21 M4）。
 * <p>
 * 每次生成都落 {@code oa_ai_copilot_task} 便于观察与重试；AI 生成内容仅供参考，
 * 业务数据只取只读摘要（{@code CopilotBizReader}），越权按 403/404 处理。
 */
@Service
@RequiredArgsConstructor
public class AiCopilotServiceImpl implements IAiCopilotService {

    /** 兜底系统提示词：未配置模板时使用，含合规声明 */
    private static final String FALLBACK_PROMPT =
        "你是企业办公助手。请基于给出的业务只读摘要生成草稿，不得编造未给出的事实。"
            + "AI 生成内容仅供参考，请人工核对后使用。";

    private final OaAiCopilotConfigMapper configMapper;
    private final OaAiCopilotTaskMapper taskMapper;
    private final OaAiModelMapper modelMapper;
    private final OaAiPromptTemplateMapper promptTemplateMapper;
    private final LlmGateway llmGateway;
    private final List<CopilotBizReader> bizReaders;
    private final List<CopilotNotifier> notifiers;

    /** 进行中的流式生成（taskId -> 会话） */
    private final Map<Long, RunSession> activeRuns = new ConcurrentHashMap<>();

    /**
     * 异步重试执行池（docs/21 AI-M4-06：长摘要/报表生成不占用 Web 线程）。
     * 有界队列 + 拒绝即丢弃并置失败，避免积压拖垮进程。
     */
    private static final java.util.concurrent.ExecutorService RETRY_EXECUTOR =
        new java.util.concurrent.ThreadPoolExecutor(1, 2, 60L, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(64), runnable -> {
                Thread thread = new Thread(runnable, "ai-copilot-retry");
                thread.setDaemon(true);
                return thread;
            }, (runnable, executor) -> {
                // 队列满：交回调用方提示稍后再试，不静默丢弃
                throw new ServiceException("AI_COPILOT_BUSY 生成队列已满，请稍后重试", 429);
            });

    private static final class RunSession {
        private volatile LlmGateway.StreamTicket ticket;
        private final StringBuilder buffer = new StringBuilder();
    }

    // ---------------------------------------------------------------- scenes

    @Override
    public List<AiCopilotSceneVo> scenes(Long userId) {
        List<OaAiCopilotConfig> configs = configMapper.selectList(new LambdaQueryWrapper<OaAiCopilotConfig>()
            .orderByAsc(OaAiCopilotConfig::getSceneCode));
        Map<String, OaAiCopilotConfig> byCode = new java.util.HashMap<>();
        for (OaAiCopilotConfig config : configs) {
            byCode.put(config.getSceneCode(), config);
        }
        List<AiCopilotSceneVo> result = new ArrayList<>();
        for (AiCopilotScene scene : AiCopilotScene.values()) {
            OaAiCopilotConfig config = byCode.get(scene.code());
            if (config == null) {
                config = bootstrap(scene);
            }
            result.add(toSceneVo(scene, config));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiCopilotSceneVo updateScene(String code, AiCopilotSceneBo bo, Long userId) {
        AiCopilotScene scene = AiCopilotScene.require(code);
        OaAiCopilotConfig config = requireConfig(scene);
        if (bo.getEnabled() != null) {
            config.setEnabled(bo.getEnabled() == 1 ? 1 : 0);
        }
        if (bo.getModelId() != null) {
            OaAiModel model = modelMapper.selectById(bo.getModelId());
            if (model == null) {
                throw new ServiceException("AI_MODEL_NOT_FOUND 模型不存在", 404);
            }
            config.setModelId(model.getId());
        }
        if (bo.getPromptTemplateId() != null) {
            OaAiPromptTemplate template = promptTemplateMapper.selectById(bo.getPromptTemplateId());
            if (template == null) {
                throw new ServiceException("AI_PROMPT_NOT_FOUND 提示词模板不存在", 404);
            }
            config.setPromptTemplateId(template.getId());
        }
        if (bo.getRemark() != null) {
            config.setRemark(bo.getRemark());
        }
        configMapper.updateById(config);
        return toSceneVo(scene, config);
    }

    // ---------------------------------------------------------------- run（SSE）

    @Override
    public RunHandle run(AiCopilotRunBo bo, Long userId, String username, StreamListener listener) {
        AiCopilotScene scene = AiCopilotScene.require(bo.getScene());
        OaAiCopilotConfig config = requireConfig(scene);
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw new ServiceException("AI_COPILOT_DISABLED 该业务助手场景已停用", 403);
        }
        String context = buildContext(scene, bo, userId);
        OaAiCopilotTask task = insertTask(scene, config, bo, userId, context);

        List<ChatTransport.Turn> turns = new ArrayList<>();
        turns.add(ChatTransport.Turn.system(systemPromptOf(config)));
        turns.add(ChatTransport.Turn.user(context));

        RunSession session = new RunSession();
        activeRuns.put(task.getId(), session);

        LlmGateway.CompletionRequest request = new LlmGateway.CompletionRequest(
            userId, username, AiBizType.COPILOT.code(), null, Long.toString(task.getId()),
            config.getModelId(), null, null, turns);

        LlmGateway.StreamTicket ticket = llmGateway.stream(request, new LlmGateway.StreamListener() {
            @Override
            public void onDelta(String delta) {
                session.buffer.append(delta);
                listener.onDelta(delta);
            }

            @Override
            public void onComplete(LlmGateway.CompletionResult result) {
                activeRuns.remove(task.getId());
                String output = result.text() == null || result.text().isBlank()
                    ? session.buffer.toString() : result.text();
                task.setOutput(output);
                task.setStatus(OaAiCopilotTask.STATUS_DONE);
                task.setPromptTokens(result.promptTokens());
                task.setTotalTokens(result.totalTokens());
                task.setFinishTime(new Date());
                taskMapper.updateById(task);
                notifyDone(task, true);
                AiCopilotResultVo vo = toResultVo(task, result);
                listener.onComplete(vo);
            }

            @Override
            public void onError(ServiceException error) {
                RunSession current = activeRuns.remove(task.getId());
                task.setOutput(current == null ? "" : current.buffer.toString());
                task.setStatus(OaAiCopilotTask.STATUS_FAILED);
                task.setErrorMsg(truncate(error.getMessage(), 500));
                task.setFinishTime(new Date());
                taskMapper.updateById(task);
                notifyDone(task, false);
                listener.onError(error);
            }
        });
        session.ticket = ticket;
        return new RunHandle() {
            @Override
            public void cancel() {
                ticket.cancel();
            }

            @Override
            public Long taskId() {
                return task.getId();
            }
        };
    }

    // ---------------------------------------------------------------- tasks

    @Override
    public PageVo<AiCopilotTaskVo> tasks(AiPageQuery page, Long userId) {
        IPage<OaAiCopilotTask> result = taskMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()),
            new LambdaQueryWrapper<OaAiCopilotTask>()
                .eq(OaAiCopilotTask::getUserId, userId)
                .orderByDesc(OaAiCopilotTask::getId));
        List<AiCopilotTaskVo> records = new ArrayList<>();
        for (OaAiCopilotTask task : result.getRecords()) {
            records.add(toTaskVo(task, false));
        }
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public AiCopilotTaskVo task(Long id, Long userId) {
        return toTaskVo(requireOwned(id, userId), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiCopilotTaskVo retry(Long taskId, Long userId, String username) {
        OaAiCopilotTask task = requireOwned(taskId, userId);
        AiCopilotScene scene = AiCopilotScene.require(task.getSceneCode());
        OaAiCopilotConfig config = requireConfig(scene);
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw new ServiceException("AI_COPILOT_DISABLED 该业务助手场景已停用", 403);
        }
        task.setStatus(OaAiCopilotTask.STATUS_PENDING);
        task.setErrorMsg(null);
        task.setFinishTime(null);
        // CAS：仅 done/failed 可重试（pending/running 即 409），防并发重复提交
        int claimed = taskMapper.update(null, new LambdaUpdateWrapper<OaAiCopilotTask>()
            .eq(OaAiCopilotTask::getId, taskId)
            .eq(OaAiCopilotTask::getUserId, userId)
            .in(OaAiCopilotTask::getStatus, OaAiCopilotTask.STATUS_DONE, OaAiCopilotTask.STATUS_FAILED)
            .set(OaAiCopilotTask::getStatus, OaAiCopilotTask.STATUS_PENDING)
            .set(OaAiCopilotTask::getErrorMsg, null)
            .set(OaAiCopilotTask::getFinishTime, null));
        if (claimed == 0) {
            throw new ServiceException("AI_COPILOT_TASK_CONFLICT 任务正在执行，稍后再试", 409);
        }

        OaAiCopilotTask asyncTask = task;
        RETRY_EXECUTOR.execute(() -> {
            // CAS pending→running：任务被删除或回收时跳过执行，不覆盖他人写入
            int acquired = taskMapper.update(null, new LambdaUpdateWrapper<OaAiCopilotTask>()
                .eq(OaAiCopilotTask::getId, asyncTask.getId())
                .eq(OaAiCopilotTask::getStatus, OaAiCopilotTask.STATUS_PENDING)
                .set(OaAiCopilotTask::getStatus, OaAiCopilotTask.STATUS_RUNNING));
            if (acquired == 0) {
                return;
            }
            boolean ok = true;
            try {
                String context = rebuildContext(scene, asyncTask);
                List<ChatTransport.Turn> turns = new ArrayList<>();
                turns.add(ChatTransport.Turn.system(systemPromptOf(config)));
                turns.add(ChatTransport.Turn.user(context));
                LlmGateway.CompletionResult result = llmGateway.chat(new LlmGateway.CompletionRequest(
                    userId, username, AiBizType.COPILOT.code(), null, Long.toString(asyncTask.getId()),
                    config.getModelId(), null, null, turns));
                asyncTask.setOutput(result.text());
                asyncTask.setStatus(OaAiCopilotTask.STATUS_DONE);
                asyncTask.setPromptTokens(result.promptTokens());
                asyncTask.setTotalTokens(result.totalTokens());
            } catch (RuntimeException e) {
                ok = false;
                asyncTask.setStatus(OaAiCopilotTask.STATUS_FAILED);
                asyncTask.setErrorMsg(truncate(e.getMessage(), 500));
            } finally {
                asyncTask.setFinishTime(new Date());
                taskMapper.updateById(asyncTask);
                notifyDone(asyncTask, ok);
            }
        });
        return toTaskVo(task, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id, Long userId) {
        OaAiCopilotTask task = requireOwned(id, userId);
        RunSession session = activeRuns.remove(task.getId());
        if (session != null && session.ticket != null) {
            session.ticket.cancel();
        }
        taskMapper.deleteById(task.getId());
    }

    // ---------------------------------------------------------------- internals

    private OaAiCopilotConfig bootstrap(AiCopilotScene scene) {
        OaAiCopilotConfig config = new OaAiCopilotConfig();
        config.setSceneCode(scene.code());
        config.setSceneName(scene.label());
        config.setEnabled(1);
        config.setRemark("内置场景");
        configMapper.insert(config);
        return config;
    }

    private OaAiCopilotConfig requireConfig(AiCopilotScene scene) {
        OaAiCopilotConfig config = configMapper.selectOne(new LambdaQueryWrapper<OaAiCopilotConfig>()
            .eq(OaAiCopilotConfig::getSceneCode, scene.code())
            .last("LIMIT 1"));
        return config == null ? bootstrap(scene) : config;
    }

    private OaAiCopilotTask insertTask(AiCopilotScene scene, OaAiCopilotConfig config,
                                       AiCopilotRunBo bo, Long userId, String context) {
        OaAiCopilotTask task = new OaAiCopilotTask();
        task.setSceneCode(scene.code());
        task.setBizType(bo.getBizType() == null || bo.getBizType().isBlank() ? scene.bizType() : bo.getBizType());
        task.setBizId(bo.getBizId());
        task.setUserId(userId);
        task.setInputRef(context);
        task.setOutput("");
        task.setStatus(OaAiCopilotTask.STATUS_RUNNING);
        task.setPromptTokens(0);
        task.setTotalTokens(0);
        taskMapper.insert(task);
        return task;
    }

    /** 组装只读上下文：用户意图 + 调用方摘要 + 业务阅读器补充（越权即抛） */
    private String buildContext(AiCopilotScene scene, AiCopilotRunBo bo, Long userId) {
        StringBuilder sb = new StringBuilder();
        if (bo.getInstruction() != null && !bo.getInstruction().isBlank()) {
            sb.append("【用户意图】\n").append(bo.getInstruction().trim()).append("\n\n");
        }
        if (bo.getContent() != null && !bo.getContent().isBlank()) {
            sb.append("【业务摘要】\n").append(bo.getContent().trim()).append("\n\n");
        }
        String fromBiz = readBizContext(scene.code(), bo.getBizId(), userId);
        if (!fromBiz.isBlank()) {
            sb.append("【系统只读摘要】\n").append(fromBiz).append('\n');
        }
        if (sb.isEmpty()) {
            throw new ServiceException("AI_COPILOT_INPUT_EMPTY 请提供业务摘要或业务 ID", 400);
        }
        return sb.toString();
    }

    /** 重试时优先用任务留存的输入上下文；缺失则按业务 ID 只读重建 */
    private String rebuildContext(AiCopilotScene scene, OaAiCopilotTask task) {
        if (task.getInputRef() != null && !task.getInputRef().isBlank()) {
            return task.getInputRef();
        }
        String fromBiz = readBizContext(scene.code(), task.getBizId(), task.getUserId());
        if (fromBiz.isBlank()) {
            throw new ServiceException("AI_COPILOT_INPUT_EMPTY 原始业务摘要不可用，无法重试", 400);
        }
        return "【系统只读摘要】\n" + fromBiz;
    }

    private String readBizContext(String sceneCode, Long bizId, Long userId) {
        for (CopilotBizReader reader : bizReaders) {
            if (sceneCode.equals(reader.scene())) {
                String context = reader.readContext(bizId, userId);
                return context == null ? "" : context;
            }
        }
        return "";
    }

    private String systemPromptOf(OaAiCopilotConfig config) {
        if (config.getPromptTemplateId() != null) {
            OaAiPromptTemplate template = promptTemplateMapper.selectById(config.getPromptTemplateId());
            if (template != null && template.getContent() != null && !template.getContent().isBlank()) {
                return template.getContent();
            }
        }
        return FALLBACK_PROMPT;
    }

    private AiCopilotSceneVo toSceneVo(AiCopilotScene scene, OaAiCopilotConfig config) {
        AiCopilotSceneVo vo = new AiCopilotSceneVo();
        vo.setCode(scene.code());
        vo.setName(config.getSceneName() == null ? scene.label() : config.getSceneName());
        vo.setDescription(scene.label() + "（AI 生成内容仅供参考，人工确认后使用）");
        vo.setEnabled(config.getEnabled());
        vo.setModelId(config.getModelId());
        if (config.getModelId() != null) {
            OaAiModel model = modelMapper.selectById(config.getModelId());
            vo.setModelName(model == null ? null : model.getAlias());
        }
        vo.setPromptTemplateId(config.getPromptTemplateId());
        if (config.getPromptTemplateId() != null) {
            OaAiPromptTemplate template = promptTemplateMapper.selectById(config.getPromptTemplateId());
            vo.setPromptTemplateName(template == null ? null : template.getName());
        }
        vo.setBizType(scene.bizType());
        vo.setConfigurable(true);
        return vo;
    }

    private AiCopilotResultVo toResultVo(OaAiCopilotTask task, LlmGateway.CompletionResult result) {
        AiCopilotResultVo vo = new AiCopilotResultVo();
        vo.setTaskId(task.getId());
        vo.setScene(task.getSceneCode());
        vo.setStatus(task.getStatus());
        vo.setContent(task.getOutput());
        vo.setPromptTokens(result == null ? 0 : result.promptTokens());
        vo.setCompletionTokens(result == null ? 0 : result.completionTokens());
        vo.setTotalTokens(result == null ? 0 : result.totalTokens());
        vo.setSources(List.of(task.getBizType() + ":" + task.getBizId()));
        return vo;
    }

    private AiCopilotTaskVo toTaskVo(OaAiCopilotTask task, boolean withOutput) {
        AiCopilotTaskVo vo = new AiCopilotTaskVo();
        vo.setId(task.getId());
        vo.setSceneCode(task.getSceneCode());
        AiCopilotScene scene = AiCopilotScene.known(task.getSceneCode())
            ? AiCopilotScene.require(task.getSceneCode()) : null;
        vo.setSceneName(scene == null ? task.getSceneCode() : scene.label());
        vo.setBizType(task.getBizType());
        vo.setBizId(task.getBizId());
        vo.setUserId(task.getUserId());
        vo.setStatus(task.getStatus());
        vo.setErrorMsg(task.getErrorMsg());
        vo.setPromptTokens(task.getPromptTokens());
        vo.setTotalTokens(task.getTotalTokens());
        vo.setFinishTime(task.getFinishTime());
        vo.setCreateTime(task.getCreateTime());
        vo.setOutput(withOutput ? task.getOutput() : null);
        return vo;
    }

    /** 任务完成后通知发起人（AI-M4-06：outbox/WS 通知；失败不影响任务） */
    private void notifyDone(OaAiCopilotTask task, boolean success) {
        String sceneName = AiCopilotScene.known(task.getSceneCode())
            ? AiCopilotScene.require(task.getSceneCode()).label() : task.getSceneCode();
        for (CopilotNotifier notifier : notifiers) {
            try {
                notifier.taskDone(task.getUserId(), sceneName, task.getId(), success);
            } catch (RuntimeException ignored) {
                // 通知失败不影响任务结果（docs/21 §8 可靠性）
            }
        }
    }

    private OaAiCopilotTask requireOwned(Long id, Long userId) {
        OaAiCopilotTask task = taskMapper.selectById(id);
        if (task == null || task.getUserId() == null || !task.getUserId().equals(userId)) {
            throw new ServiceException("AI_COPILOT_TASK_NOT_FOUND 任务不存在", 404);
        }
        return task;
    }

    private static String truncate(String message, int max) {
        if (message == null) {
            return null;
        }
        return message.length() <= max ? message : message.substring(0, max);
    }
}

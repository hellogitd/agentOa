package org.dromara.agentoa.ai.service.support;

import org.dromara.agentoa.ai.domain.OaAiAgent;
import org.dromara.agentoa.ai.domain.enums.AiBizType;
import org.dromara.agentoa.ai.domain.vo.AiAgentTraceStepVo;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 多步执行引擎（docs/21 AI-M5-03/04/05）。
 * <p>
 * 循环：模型给出工具调用 → 执行工具 → 回填结果 → 直到模型给出最终回答或达到
 * {@code max_steps} / 超时；每一步写入执行轨迹，供 UI 观察与回放。
 * 写类工具必须由调用方显式确认（approved）并持有 {@code ai:tool:write}。
 */
@Component
public class AgentEngine {

    /** 工具结果回填给模型的长度上限 */
    private static final int MAX_TOOL_RESULT_CHARS = 4000;

    /** 执行回调：轨迹逐步推送 */
    public interface Listener {
        void onStep(AiAgentTraceStepVo step);

        void onDelta(String delta);

        void onComplete(String output, int totalTokens, List<AiAgentTraceStepVo> trace);

        void onError(ServiceException error);
    }

    /** 运行句柄 */
    public interface RunHandle {
        void cancel();

        boolean isCancelled();
    }

    private final LlmGateway llmGateway;
    private final AgentToolExecutor toolExecutor;

    public AgentEngine(LlmGateway llmGateway, AgentToolExecutor toolExecutor) {
        this.llmGateway = llmGateway;
        this.toolExecutor = toolExecutor;
    }

    /**
     * 启动一次 Agent 运行（异步执行，回调在工作线程）。
     *
     * @param agent        Agent 配置
     * @param toolSpecs    暴露给模型的工具声明
     * @param toolCodes    与 toolSpecs 对应的工具码（执行路由）
     * @param approvedWriteTools 本次运行人工逐项确认过的写类工具码
     */
    public RunHandle run(OaAiAgent agent, List<ChatTransport.ToolSpec> toolSpecs, List<String> toolCodes,
                         String input, Long userId, String username, Set<String> permissions,
                         Set<String> approvedWriteTools, Listener listener) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        long deadline = System.currentTimeMillis() + timeoutMillis(agent);
        Thread worker = new Thread(() -> execute(agent, toolSpecs, toolCodes, input, userId, username,
            permissions, approvedWriteTools, cancelled, deadline, listener), "ai-agent-run");
        worker.setDaemon(true);
        worker.start();
        return new RunHandle() {
            @Override
            public void cancel() {
                cancelled.set(true);
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };
    }

    // ---------------------------------------------------------------- internals

    private void execute(OaAiAgent agent, List<ChatTransport.ToolSpec> toolSpecs, List<String> toolCodes,
                         String input, Long userId, String username, Set<String> permissions,
                         Set<String> approvedWriteTools, AtomicBoolean cancelled, long deadline, Listener listener) {
        List<AiAgentTraceStepVo> trace = new ArrayList<>();
        StringBuilder output = new StringBuilder();
        int totalTokens = 0;
        int maxSteps = agent.getMaxSteps() == null || agent.getMaxSteps() <= 0 ? 6 : agent.getMaxSteps();
        try {
            List<ChatTransport.Turn> turns = new ArrayList<>();
            turns.add(ChatTransport.Turn.system(systemPromptOf(agent, toolCodes)));
            turns.add(ChatTransport.Turn.user(input));
            int step = 0;
            while (step < maxSteps) {
                step++;
                if (cancelled.get()) {
                    listener.onComplete(output.toString(), totalTokens, trace);
                    return;
                }
                if (System.currentTimeMillis() > deadline) {
                    throw new ServiceException("AI_AGENT_TIMEOUT Agent 执行超时", 504);
                }
                LlmGateway.CompletionResult result = llmGateway.chat(new LlmGateway.CompletionRequest(
                    userId, username, AiBizType.AGENT.code(), null, null, agent.getModelId(),
                    null, null, turns, toolSpecs));
                totalTokens += result.totalTokens();

                List<ChatTransport.ToolCall> calls = result.toolCalls() == null ? List.of() : result.toolCalls();
                if (calls.isEmpty()) {
                    output.append(result.text() == null ? "" : result.text());
                    emitDelta(listener, result.text());
                    AiAgentTraceStepVo finalStep = new AiAgentTraceStepVo();
                    finalStep.setStep(step);
                    finalStep.setType("final");
                    finalStep.setResult(truncate(result.text(), MAX_TOOL_RESULT_CHARS));
                    trace.add(finalStep);
                    listener.onStep(finalStep);
                    listener.onComplete(output.toString(), totalTokens, trace);
                    return;
                }
                turns.add(ChatTransport.Turn.assistantToolCalls(result.text(), calls));
                for (ChatTransport.ToolCall call : calls) {
                    AiAgentTraceStepVo callStep = new AiAgentTraceStepVo();
                    callStep.setStep(step);
                    callStep.setType("tool_call");
                    callStep.setTool(call.name());
                    callStep.setArguments(call.argumentsJson());
                    callStep.setWriteTool(toolExecutor.isWriteTool(call.name()));
                    trace.add(callStep);
                    listener.onStep(callStep);

                    AgentToolExecutor.ToolOutcome outcome = toolExecutor.execute(
                        call.name(), call.argumentsJson(), userId, username, approvedWriteTools, permissions);
                    AiAgentTraceStepVo resultStep = new AiAgentTraceStepVo();
                    resultStep.setStep(step);
                    resultStep.setType("tool_result");
                    resultStep.setTool(outcome.tool());
                    resultStep.setResult(truncate(outcome.result(), MAX_TOOL_RESULT_CHARS));
                    resultStep.setWriteTool(outcome.writeTool());
                    resultStep.setDurationMs(outcome.durationMs());
                    trace.add(resultStep);
                    listener.onStep(resultStep);

                    turns.add(ChatTransport.Turn.toolResult(call.id(), call.name(),
                        truncate(outcome.result(), MAX_TOOL_RESULT_CHARS)));
                }
            }
            throw new ServiceException("AI_AGENT_STEPS_EXCEEDED 超过最大执行步数", 400);
        } catch (ServiceException e) {
            listener.onError(e);
        } catch (RuntimeException e) {
            listener.onError(new ServiceException("AI_AGENT_FAILED Agent 执行失败：" + safeMessage(e), 502));
        }
    }

    private void emitDelta(Listener listener, String text) {
        if (text != null && !text.isBlank()) {
            listener.onDelta(text);
        }
    }

    private String systemPromptOf(OaAiAgent agent, List<String> toolCodes) {
        StringBuilder sb = new StringBuilder();
        sb.append(agent.getSystemPrompt() == null ? "" : agent.getSystemPrompt()).append('\n');
        sb.append("你可用的工具：").append(toolCodes == null ? "无" : String.join("、", toolCodes)).append("。\n");
        sb.append("需要工具时请发起工具调用；工具结果仅供整理，最终回答要说明信息来源。");
        sb.append("AI 生成内容仅供参考，请人工核对后使用。");
        return sb.toString();
    }

    private long timeoutMillis(OaAiAgent agent) {
        int seconds = agent.getTimeoutSec() == null || agent.getTimeoutSec() <= 0 ? 60 : agent.getTimeoutSec();
        return seconds * 1000L;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }
}

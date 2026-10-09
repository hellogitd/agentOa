package org.dromara.agentoa.ai;

import org.dromara.agentoa.ai.domain.OaAiAgent;
import org.dromara.agentoa.ai.domain.OaAiAgentRun;
import org.dromara.agentoa.ai.domain.OaAiTool;
import org.dromara.agentoa.ai.domain.bo.AiAgentBo;
import org.dromara.agentoa.ai.domain.bo.AiAgentRunBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiToolBo;
import org.dromara.agentoa.ai.domain.vo.AiAgentRunVo;
import org.dromara.agentoa.ai.domain.vo.AiAgentTraceStepVo;
import org.dromara.agentoa.ai.domain.vo.AiAgentVo;
import org.dromara.agentoa.ai.domain.vo.AiToolTestVo;
import org.dromara.agentoa.ai.domain.vo.AiToolVo;
import org.dromara.agentoa.ai.service.IAiAgentService;
import org.dromara.agentoa.ai.service.support.AgentTool;
import org.dromara.agentoa.ai.service.support.ChatTransport;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.ai.support.AiTestEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M5 工具与 Agent 契约（docs/21 §7.4）：工具权限边界（内置锁定、码冲突 409）、
 * MCP/函数连通性测试、Agent 多步执行轨迹、写类工具需人工确认、运行记录仅本人可见。
 */
class AiAgentToolH2Test {

    @BeforeAll
    static void boot() {
        AiTestEnvironment.bootstrap();
    }

    @BeforeEach
    void reset() {
        AiTestEnvironment.usageRecorder.flush();
        AiTestEnvironment.clearData();
        AiTestEnvironment.rebuildQuotaGuard(100_000L, 500);
        AiTestEnvironment.seedDefaultModel();
    }

    // ---------------------------------------------------------------- tools

    @Test
    void toolCrudAndCodeConflict() {
        AiToolBo bo = new AiToolBo();
        bo.setCode("my_tool");
        bo.setName("自研工具");
        bo.setType("function");
        bo.setDescription("测试工具");
        bo.setSchemaJson("{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}}}");
        AiToolVo created = AiTestEnvironment.toolService.create(bo);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getWriteFlag()).isEqualTo(0);

        assertThatThrownBy(() -> AiTestEnvironment.toolService.create(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_TOOL_CODE_EXISTS");

        bo.setId(created.getId());
        bo.setName("自研工具 v2");
        AiToolVo updated = AiTestEnvironment.toolService.update(bo);
        assertThat(updated.getName()).isEqualTo("自研工具 v2");

        AiTestEnvironment.toolService.delete(created.getId());
        PageVo<AiToolVo> page = AiTestEnvironment.toolService.page(new AiPageQuery());
        assertThat(page.getRecords()).isEmpty();
    }

    @Test
    void builtinToolCannotBeDeleted() {
        OaAiTool builtin = new OaAiTool();
        builtin.setCode("knowledge_search");
        builtin.setName("知识库检索");
        builtin.setType(OaAiTool.TYPE_FUNCTION);
        builtin.setWriteFlag(0);
        builtin.setEnabled(1);
        builtin.setIsBuiltin(1);
        AiTestEnvironment.tools.insert(builtin);

        assertThatThrownBy(() -> AiTestEnvironment.toolService.delete(builtin.getId()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_TOOL_BUILTIN_LOCKED");

        AiToolBo bo = new AiToolBo();
        bo.setId(builtin.getId());
        bo.setEnabled(0);
        AiToolVo updated = AiTestEnvironment.toolService.update(bo);
        assertThat(updated.getEnabled()).isEqualTo(0);
    }

    @Test
    void functionToolTestReportsMissingImplementation() {
        AiToolBo bo = new AiToolBo();
        bo.setCode("ghost_tool");
        bo.setName("无实现工具");
        bo.setType("function");
        AiToolVo created = AiTestEnvironment.toolService.create(bo);

        AiToolTestVo test = AiTestEnvironment.toolService.test(created.getId());
        assertThat(test.getOk()).isFalse();
        assertThat(test.getMessage()).contains("缺少对应的 Java 实现");
    }

    @Test
    void mcpConfigMaskedInList() {
        AiToolBo bo = new AiToolBo();
        bo.setCode("remote_mcp");
        bo.setName("外部 MCP");
        bo.setType("mcp");
        bo.setConfigJson("{\"transport\":\"http\",\"url\":\"http://127.0.0.1:1/mcp\",\"headers\":{\"Authorization\":\"Bearer secret\"}}");
        AiToolVo created = AiTestEnvironment.toolService.create(bo);

        AiToolVo loaded = AiTestEnvironment.toolService.get(created.getId());
        assertThat(loaded.getEndpoint()).isEqualTo("http://127.0.0.1:1/mcp");
        assertThat(loaded.getHasAuthHeader()).isTrue();
        // 鉴权头永不回显（docs/21 §2.3）
        assertThat(String.valueOf(loaded)).doesNotContain("secret");
    }

    // ---------------------------------------------------------------- agents

    @Test
    void agentCrudAndBuiltinLock() {
        AiAgentBo bo = new AiAgentBo();
        bo.setCode("my_agent");
        bo.setName("自定义助手");
        bo.setSystemPrompt("你是助手");
        bo.setToolCodes(List.of("knowledge_search"));
        bo.setMaxSteps(3);
        bo.setTimeoutSec(30);
        AiAgentVo created = AiTestEnvironment.agentService.create(bo);
        assertThat(created.getToolCodes()).containsExactly("knowledge_search");

        OaAiAgent builtin = new OaAiAgent();
        builtin.setCode("office_assistant");
        builtin.setName("办公助手");
        builtin.setSystemPrompt("内置");
        builtin.setMaxSteps(6);
        builtin.setTimeoutSec(60);
        builtin.setEnabled(1);
        builtin.setIsBuiltin(1);
        AiTestEnvironment.agents.insert(builtin);

        assertThatThrownBy(() -> AiTestEnvironment.agentService.delete(builtin.getId()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_AGENT_BUILTIN_LOCKED");

        AiTestEnvironment.agentService.delete(created.getId());
        assertThat(AiTestEnvironment.agentService.page(new AiPageQuery()).getRecords()).hasSize(1);
    }

    // ---------------------------------------------------------------- run + trace

    @Test
    void agentRunRecordsToolTrace() throws Exception {
        registerEchoTool(false);
        OaAiAgent agent = seedAgent("echo_agent", "[\"echo_tool\"]", 4);

        // 第一步发起工具调用，第二步给出最终回答
        AtomicInteger calls = new AtomicInteger();
        AiTestEnvironment.transport.completeHandler = call -> {
            assertThat(call.tools()).hasSize(1);
            assertThat(call.tools().get(0).name()).isEqualTo("echo_tool");
            if (calls.incrementAndGet() == 1) {
                return new ChatTransport.Result("", 3, 5, 8,
                    List.of(new ChatTransport.ToolCall("call-1", "echo_tool", "{\"q\":\"hi\"}")));
            }
            return new ChatTransport.Result("最终回答", 3, 5, 8);
        };

        CollectingRunListener listener = new CollectingRunListener();
        IAiAgentService.RunHandle handle = AiTestEnvironment.agentService.run(agent.getId(),
            runBo("查一下"), AiTestEnvironment.USER_A, "u100", listener);
        assertThat(listener.await()).isTrue();
        assertThat(handle.runId()).isNotNull();

        AiAgentRunVo detail = AiTestEnvironment.agentService.runDetail(handle.runId(), AiTestEnvironment.USER_A);
        assertThat(detail.getStatus()).isEqualTo(OaAiAgentRun.STATUS_DONE);
        assertThat(detail.getOutput()).isEqualTo("最终回答");
        assertThat(detail.getTrace()).extracting(AiAgentTraceStepVo::getType)
            .contains("tool_call", "tool_result", "final");
        assertThat(listener.steps).extracting(AiAgentTraceStepVo::getTool).contains("echo_tool");
    }

    @Test
    void writeToolRequiresApproval() throws Exception {
        registerEchoTool(true);
        OaAiAgent agent = seedAgent("write_agent", "[\"echo_tool\"]", 2);
        AtomicInteger calls = new AtomicInteger();
        AiTestEnvironment.transport.completeHandler = call -> {
            if (calls.incrementAndGet() == 1) {
                return new ChatTransport.Result("", 1, 1, 2,
                    List.of(new ChatTransport.ToolCall("call-1", "echo_tool", "{}")));
            }
            return new ChatTransport.Result("已给出建议", 1, 1, 2);
        };

        CollectingRunListener listener = new CollectingRunListener();
        IAiAgentService.RunHandle handle = AiTestEnvironment.agentService.run(agent.getId(), runBo("创建日程"),
            AiTestEnvironment.USER_A, "u100", listener);
        assertThat(listener.await()).isTrue();

        AiAgentRunVo detail = AiTestEnvironment.agentService.runDetail(handle.runId(), AiTestEnvironment.USER_A);
        List<AiAgentTraceStepVo> results = detail.getTrace().stream()
            .filter(step -> "tool_result".equals(step.getType())).toList();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getResult()).contains("需人工确认");
    }

    @Test
    void writeToolApprovalIsPerTool() throws Exception {
        registerTool("read_tool", false);
        registerTool("write_tool", true);
        OaAiAgent agent = seedAgent("per_tool_agent", "[\"read_tool\",\"write_tool\"]", 3);
        AtomicInteger calls = new AtomicInteger();
        // 第一步发起工具调用，第二步给出最终回答（避免步数熔断）
        AiTestEnvironment.transport.completeHandler = call -> {
            if (calls.incrementAndGet() == 1) {
                return new ChatTransport.Result("", 1, 1, 2,
                    List.of(new ChatTransport.ToolCall("call-1", "write_tool", "{}")));
            }
            return new ChatTransport.Result("done", 1, 1, 2);
        };

        // 只确认了 read_tool，write_tool 仍被拦下（按工具逐项确认）
        CollectingRunListener listener = new CollectingRunListener();
        AiAgentRunBo bo = runBo("创建日程");
        bo.setApprovedWriteTools(List.of("read_tool"));
        IAiAgentService.RunHandle handle = AiTestEnvironment.agentService.run(agent.getId(), bo,
            AiTestEnvironment.USER_A, "u100", listener);
        assertThat(listener.await()).isTrue();
        AiAgentRunVo detail = AiTestEnvironment.agentService.runDetail(handle.runId(), AiTestEnvironment.USER_A);
        assertThat(detail.getTrace().stream()
            .filter(step -> "tool_result".equals(step.getType()))
            .map(AiAgentTraceStepVo::getResult).toList())
            .allSatisfy(result -> assertThat(result).contains("需人工确认"));

        // 确认了 write_tool 后进入权限闸（测试上下文无 ai:tool:write）
        calls.set(0);
        CollectingRunListener second = new CollectingRunListener();
        AiAgentRunBo approved = runBo("创建日程");
        approved.setApprovedWriteTools(List.of("write_tool"));
        IAiAgentService.RunHandle approvedHandle = AiTestEnvironment.agentService.run(agent.getId(), approved,
            AiTestEnvironment.USER_A, "u100", second);
        assertThat(second.await()).isTrue();
        AiAgentRunVo approvedDetail = AiTestEnvironment.agentService.runDetail(approvedHandle.runId(), AiTestEnvironment.USER_A);
        assertThat(approvedDetail.getTrace().stream()
            .filter(step -> "tool_result".equals(step.getType()))
            .map(AiAgentTraceStepVo::getResult).toList())
            .allSatisfy(result -> assertThat(result).contains("ai:tool:write"));
    }

    @Test
    void runVisibleOnlyToOwner() throws Exception {
        registerEchoTool(false);
        OaAiAgent agent = seedAgent("owner_agent", "[\"echo_tool\"]", 2);
        AiTestEnvironment.transport.completeHandler = call -> new ChatTransport.Result("done", 1, 1, 2);

        CollectingRunListener listener = new CollectingRunListener();
        IAiAgentService.RunHandle handle = AiTestEnvironment.agentService.run(agent.getId(), runBo("hi"),
            AiTestEnvironment.USER_A, "u100", listener);
        listener.await();

        assertThatThrownBy(() -> AiTestEnvironment.agentService.runDetail(handle.runId(), AiTestEnvironment.USER_B))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_AGENT_RUN_NOT_FOUND");

        PageVo<AiAgentRunVo> mine = AiTestEnvironment.agentService.runs(null, new AiPageQuery(), AiTestEnvironment.USER_A);
        assertThat(mine.getRecords()).hasSize(1);
        PageVo<AiAgentRunVo> others = AiTestEnvironment.agentService.runs(null, new AiPageQuery(), AiTestEnvironment.USER_B);
        assertThat(others.getRecords()).isEmpty();
    }

    @Test
    void disabledAgentRejectsRun() {
        OaAiAgent agent = seedAgent("off_agent", "[]", 2);
        AiAgentBo bo = new AiAgentBo();
        bo.setId(agent.getId());
        bo.setEnabled(0);
        AiTestEnvironment.agentService.update(bo);

        assertThatThrownBy(() -> AiTestEnvironment.agentService.run(agent.getId(), runBo("hi"),
            AiTestEnvironment.USER_A, "u100", new CollectingRunListener()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_AGENT_DISABLED");
    }

    // ---------------------------------------------------------------- 熔断与 MCP 归一化

    @Test
    void runCircuitBreaksWhenMaxStepsExceeded() throws Exception {
        registerEchoTool(false);
        OaAiAgent agent = seedAgent("steps_agent", "[\"echo_tool\"]", 2);
        // 模型每步都发起工具调用：2 步后触发步数熔断
        AiTestEnvironment.transport.completeHandler = call -> new ChatTransport.Result("", 1, 1, 2,
            List.of(new ChatTransport.ToolCall("call-" + System.nanoTime(), "echo_tool", "{}")));

        CollectingRunListener listener = new CollectingRunListener();
        IAiAgentService.RunHandle handle = AiTestEnvironment.agentService.run(agent.getId(), runBo("hi"),
            AiTestEnvironment.USER_A, "u100", listener);
        listener.await();

        assertThat(listener.error).isNotNull();
        assertThat(listener.error.getMessage()).contains("AI_AGENT_STEPS_EXCEEDED");
        AiAgentRunVo detail = AiTestEnvironment.agentService.runDetail(handle.runId(), AiTestEnvironment.USER_A);
        assertThat(detail.getStatus()).isEqualTo(OaAiAgentRun.STATUS_FAILED);
        assertThat(detail.getErrorMsg()).contains("AI_AGENT_STEPS_EXCEEDED");
    }

    @Test
    void runCircuitBreaksWhenDeadlinePasses() throws Exception {
        registerEchoTool(false);
        OaAiAgent agent = seedAgent("timeout_agent", "[\"echo_tool\"]", 3);
        agent.setTimeoutSec(1);
        AiTestEnvironment.agents.updateById(agent);
        // 首步模型调用耗时超过 timeoutSec，第二步开始即触发超时熔断
        AiTestEnvironment.transport.completeHandler = call -> {
            try {
                Thread.sleep(1_100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new ChatTransport.Result("", 1, 1, 2,
                List.of(new ChatTransport.ToolCall("call-1", "echo_tool", "{}")));
        };

        CollectingRunListener listener = new CollectingRunListener();
        AiTestEnvironment.agentService.run(agent.getId(), runBo("hi"), AiTestEnvironment.USER_A, "u100", listener);
        listener.await();

        assertThat(listener.error).isNotNull();
        assertThat(listener.error.getMessage()).contains("AI_AGENT_TIMEOUT");
    }

    @Test
    void mcpConnectFailureIsNormalizedTo502() {
        AiToolBo bo = new AiToolBo();
        bo.setCode("unreachable_mcp");
        bo.setName("不可达 MCP");
        bo.setType("mcp");
        bo.setConfigJson("{\"transport\":\"http\",\"url\":\"http://127.0.0.1:1/mcp\"}");
        AiToolVo created = AiTestEnvironment.toolService.create(bo);

        // 建连失败归一化为 AI_MCP_CONNECT_FAILED 502（不裸抛 500）
        assertThatThrownBy(() -> AiTestEnvironment.toolService.discover(created.getId()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_MCP_CONNECT_FAILED")
            .satisfies(error -> assertThat(((ServiceException) error).getCode()).isEqualTo(502));
        // 连通性测试以诊断结果回传，不抛异常
        AiToolTestVo test = AiTestEnvironment.toolService.test(created.getId());
        assertThat(test.getOk()).isFalse();
        assertThat(test.getMessage()).contains("AI_MCP_CONNECT_FAILED");
    }

    // ---------------------------------------------------------------- helpers

    private void registerEchoTool(boolean writeTool) {
        registerTool("echo_tool", writeTool);
    }

    /** 登记一个内置函数工具（注册表 + oa_ai_tool 行） */
    private void registerTool(String code, boolean writeTool) {
        AiTestEnvironment.functionToolRegistry.register(new AgentTool() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public String name() {
                return code;
            }

            @Override
            public String description() {
                return "测试用工具";
            }

            @Override
            public String parametersJsonSchema() {
                return "{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}}}";
            }

            @Override
            public boolean writeTool() {
                return writeTool;
            }

            @Override
            public String execute(String argumentsJson, Long userId, String username) {
                return "echo:" + argumentsJson;
            }
        });
        OaAiTool tool = new OaAiTool();
        tool.setCode(code);
        tool.setName(code);
        tool.setType(OaAiTool.TYPE_FUNCTION);
        tool.setDescription("测试用工具");
        tool.setSchemaJson("{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}}}");
        tool.setWriteFlag(writeTool ? 1 : 0);
        tool.setEnabled(1);
        tool.setIsBuiltin(0);
        AiTestEnvironment.tools.insert(tool);
    }

    private OaAiAgent seedAgent(String code, String toolCodes, int maxSteps) {
        OaAiAgent agent = new OaAiAgent();
        agent.setCode(code);
        agent.setName(code);
        agent.setSystemPrompt("你是测试助手");
        agent.setToolCodes(toolCodes);
        agent.setMaxSteps(maxSteps);
        agent.setTimeoutSec(30);
        agent.setEnabled(1);
        agent.setIsBuiltin(0);
        AiTestEnvironment.agents.insert(agent);
        return agent;
    }

    private AiAgentRunBo runBo(String input) {
        AiAgentRunBo bo = new AiAgentRunBo();
        bo.setInput(input);
        return bo;
    }

    private static final class CollectingRunListener implements IAiAgentService.RunListener {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final List<AiAgentTraceStepVo> steps = new CopyOnWriteArrayList<>();
        private final StringBuilder deltas = new StringBuilder();
        private volatile AiAgentRunVo run;
        private volatile ServiceException error;

        @Override
        public void onStep(AiAgentTraceStepVo step) {
            steps.add(step);
        }

        @Override
        public void onDelta(String delta) {
            deltas.append(delta);
        }

        @Override
        public void onComplete(AiAgentRunVo value) {
            run = value;
            latch.countDown();
        }

        @Override
        public void onError(ServiceException value) {
            error = value;
            latch.countDown();
        }

        boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS) && error == null;
        }
    }
}

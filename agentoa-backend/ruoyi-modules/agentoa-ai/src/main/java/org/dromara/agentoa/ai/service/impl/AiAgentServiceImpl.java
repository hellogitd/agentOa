package org.dromara.agentoa.ai.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiAgent;
import org.dromara.agentoa.ai.domain.OaAiAgentRun;
import org.dromara.agentoa.ai.domain.OaAiModel;
import org.dromara.agentoa.ai.domain.OaAiTool;
import org.dromara.agentoa.ai.domain.bo.AiAgentBo;
import org.dromara.agentoa.ai.domain.bo.AiAgentRunBo;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.vo.AiAgentRunVo;
import org.dromara.agentoa.ai.domain.vo.AiAgentTraceStepVo;
import org.dromara.agentoa.ai.domain.vo.AiAgentVo;
import org.dromara.agentoa.ai.mapper.OaAiAgentMapper;
import org.dromara.agentoa.ai.mapper.OaAiAgentRunMapper;
import org.dromara.agentoa.ai.mapper.OaAiModelMapper;
import org.dromara.agentoa.ai.mapper.OaAiToolMapper;
import org.dromara.agentoa.ai.service.IAiAgentService;
import org.dromara.agentoa.ai.service.support.AgentEngine;
import org.dromara.agentoa.ai.service.support.AgentToolExecutor;
import org.dromara.agentoa.ai.service.support.ChatTransport;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent 配置与运行实现（docs/21 AI-M5-03/04/05）。
 * <p>
 * 运行记录只存本人可见；执行轨迹逐步落库，便于观察与回放。写类工具需人工确认。
 */
@Service
@RequiredArgsConstructor
public class AiAgentServiceImpl implements IAiAgentService {

    private final OaAiAgentMapper agentMapper;
    private final OaAiAgentRunMapper runMapper;
    private final OaAiToolMapper toolMapper;
    private final OaAiModelMapper modelMapper;
    private final AgentEngine agentEngine;

    @Override
    public PageVo<AiAgentVo> page(AiPageQuery page) {
        IPage<OaAiAgent> result = agentMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()),
            new LambdaQueryWrapper<OaAiAgent>().orderByAsc(OaAiAgent::getCode));
        List<AiAgentVo> records = new ArrayList<>();
        for (OaAiAgent agent : result.getRecords()) {
            records.add(toVo(agent));
        }
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public AiAgentVo get(Long id) {
        return toVo(require(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiAgentVo create(AiAgentBo bo) {
        OaAiAgent exists = agentMapper.selectOne(new LambdaQueryWrapper<OaAiAgent>()
            .eq(OaAiAgent::getCode, bo.getCode())
            .last("LIMIT 1"));
        if (exists != null) {
            throw new ServiceException("AI_AGENT_CODE_EXISTS Agent 码已存在", 409);
        }
        OaAiAgent agent = new OaAiAgent();
        apply(agent, bo);
        agent.setIsBuiltin(0);
        agentMapper.insert(agent);
        return toVo(agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiAgentVo update(AiAgentBo bo) {
        OaAiAgent agent = require(bo.getId());
        if (agent.getIsBuiltin() != null && agent.getIsBuiltin() == 1
            && bo.getCode() != null && !bo.getCode().equals(agent.getCode())) {
            throw new ServiceException("AI_AGENT_BUILTIN_LOCKED 内置 Agent 不可改码", 409);
        }
        if (bo.getCode() != null && !bo.getCode().equals(agent.getCode())) {
            OaAiAgent exists = agentMapper.selectOne(new LambdaQueryWrapper<OaAiAgent>()
                .eq(OaAiAgent::getCode, bo.getCode())
                .last("LIMIT 1"));
            if (exists != null && !exists.getId().equals(agent.getId())) {
                throw new ServiceException("AI_AGENT_CODE_EXISTS Agent 码已存在", 409);
            }
        }
        apply(agent, bo);
        agentMapper.updateById(agent);
        return toVo(agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OaAiAgent agent = require(id);
        if (agent.getIsBuiltin() != null && agent.getIsBuiltin() == 1) {
            throw new ServiceException("AI_AGENT_BUILTIN_LOCKED 内置 Agent 不可删除", 409);
        }
        agentMapper.deleteById(id);
    }

    // ---------------------------------------------------------------- run

    @Override
    public RunHandle run(Long agentId, AiAgentRunBo bo, Long userId, String username, RunListener listener) {
        OaAiAgent agent = require(agentId);
        if (agent.getEnabled() == null || agent.getEnabled() != 1) {
            throw new ServiceException("AI_AGENT_DISABLED Agent 已停用", 403);
        }
        List<String> toolCodes = parseToolCodes(agent.getToolCodes());
        List<OaAiTool> tools = resolveTools(toolCodes);
        List<ChatTransport.ToolSpec> specs = new ArrayList<>();
        List<String> specCodes = new ArrayList<>();
        for (OaAiTool tool : tools) {
            specs.add(new ChatTransport.ToolSpec(tool.getCode(),
                tool.getDescription() == null ? tool.getName() : tool.getDescription(),
                tool.getSchemaJson()));
            specCodes.add(tool.getCode());
        }

        OaAiAgentRun run = new OaAiAgentRun();
        run.setAgentId(agent.getId());
        run.setUserId(userId);
        run.setConversationId(bo.getConversationId());
        run.setInput(bo.getInput());
        run.setTraceJson("[]");
        run.setOutput("");
        run.setStatus(OaAiAgentRun.STATUS_RUNNING);
        run.setTotalTokens(0);
        run.setDurationMs(0);
        runMapper.insert(run);

        List<AiAgentTraceStepVo> trace = new ArrayList<>();
        Set<String> approvedWriteTools = bo.getApprovedWriteTools() == null
            ? Set.of() : new LinkedHashSet<>(bo.getApprovedWriteTools());
        long started = System.currentTimeMillis();
        AgentEngine.RunHandle handle = agentEngine.run(agent, specs, specCodes, bo.getInput(), userId, username,
            AgentToolExecutor.currentPermissions(), approvedWriteTools, new AgentEngine.Listener() {
                @Override
                public void onStep(AiAgentTraceStepVo step) {
                    synchronized (trace) {
                        trace.add(step);
                    }
                    listener.onStep(step);
                }

                @Override
                public void onDelta(String delta) {
                    listener.onDelta(delta);
                }

                @Override
                public void onComplete(String output, int totalTokens, List<AiAgentTraceStepVo> steps) {
                    run.setOutput(output);
                    run.setTotalTokens(totalTokens);
                    run.setStatus(OaAiAgentRun.STATUS_DONE);
                    run.setDurationMs((int) (System.currentTimeMillis() - started));
                    run.setTraceJson(JsonUtils.toJsonString(steps));
                    runMapper.updateById(run);
                    listener.onComplete(toRunVo(run, agent, true));
                }

                @Override
                public void onError(ServiceException error) {
                    run.setStatus(OaAiAgentRun.STATUS_FAILED);
                    run.setErrorMsg(truncate(error.getMessage(), 500));
                    run.setDurationMs((int) (System.currentTimeMillis() - started));
                    synchronized (trace) {
                        run.setTraceJson(JsonUtils.toJsonString(trace));
                    }
                    runMapper.updateById(run);
                    listener.onError(error);
                }
            });

        return new RunHandle() {
            @Override
            public void cancel() {
                handle.cancel();
            }

            @Override
            public Long runId() {
                return run.getId();
            }
        };
    }

    @Override
    public PageVo<AiAgentRunVo> runs(Long agentId, AiPageQuery page, Long userId) {
        boolean seeAll = superAdmin();
        LambdaQueryWrapper<OaAiAgentRun> wrapper = new LambdaQueryWrapper<OaAiAgentRun>()
            .orderByDesc(OaAiAgentRun::getId);
        if (agentId != null) {
            wrapper.eq(OaAiAgentRun::getAgentId, agentId);
        }
        if (!seeAll) {
            wrapper.eq(OaAiAgentRun::getUserId, userId);
        }
        IPage<OaAiAgentRun> result = runMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()), wrapper);
        List<AiAgentRunVo> records = new ArrayList<>();
        for (OaAiAgentRun run : result.getRecords()) {
            records.add(toRunVo(run, null, false));
        }
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public AiAgentRunVo runDetail(Long runId, Long userId) {
        OaAiAgentRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new ServiceException("AI_AGENT_RUN_NOT_FOUND 运行记录不存在", 404);
        }
        if (!superAdmin() && (run.getUserId() == null || !run.getUserId().equals(userId))) {
            throw new ServiceException("AI_AGENT_RUN_NOT_FOUND 运行记录不存在", 404);
        }
        OaAiAgent agent = run.getAgentId() == null ? null : agentMapper.selectById(run.getAgentId());
        return toRunVo(run, agent, true);
    }

    // ---------------------------------------------------------------- internals

    private void apply(OaAiAgent agent, AiAgentBo bo) {
        if (bo.getCode() != null) {
            agent.setCode(bo.getCode());
        }
        if (bo.getName() != null) {
            agent.setName(bo.getName());
        }
        if (bo.getSystemPrompt() != null) {
            agent.setSystemPrompt(bo.getSystemPrompt());
        }
        if (bo.getModelId() != null) {
            agent.setModelId(bo.getModelId());
        }
        if (bo.getToolCodes() != null) {
            agent.setToolCodes(JsonUtils.toJsonString(bo.getToolCodes()));
        }
        if (bo.getMaxSteps() != null) {
            agent.setMaxSteps(bo.getMaxSteps());
        }
        if (bo.getTimeoutSec() != null) {
            agent.setTimeoutSec(bo.getTimeoutSec());
        }
        if (bo.getEnabled() != null) {
            agent.setEnabled(bo.getEnabled() == 1 ? 1 : 0);
        }
        if (bo.getRemark() != null) {
            agent.setRemark(bo.getRemark());
        }
    }

    private OaAiAgent require(Long id) {
        OaAiAgent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new ServiceException("AI_AGENT_NOT_FOUND Agent 不存在", 404);
        }
        return agent;
    }

    private List<String> parseToolCodes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JsonUtils.parseArray(json, String.class);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private List<OaAiTool> resolveTools(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>(codes);
        List<OaAiTool> result = new ArrayList<>();
        for (String code : unique) {
            OaAiTool tool = toolMapper.selectOne(new LambdaQueryWrapper<OaAiTool>()
                .eq(OaAiTool::getCode, code)
                .eq(OaAiTool::getEnabled, 1)
                .last("LIMIT 1"));
            if (tool != null) {
                result.add(tool);
            }
        }
        return result;
    }

    private AiAgentVo toVo(OaAiAgent agent) {
        AiAgentVo vo = new AiAgentVo();
        vo.setId(agent.getId());
        vo.setCode(agent.getCode());
        vo.setName(agent.getName());
        vo.setSystemPrompt(agent.getSystemPrompt());
        vo.setModelId(agent.getModelId());
        if (agent.getModelId() != null) {
            OaAiModel model = modelMapper.selectById(agent.getModelId());
            vo.setModelName(model == null ? null : model.getAlias());
        }
        vo.setToolCodes(parseToolCodes(agent.getToolCodes()));
        vo.setMaxSteps(agent.getMaxSteps());
        vo.setTimeoutSec(agent.getTimeoutSec());
        vo.setEnabled(agent.getEnabled());
        vo.setIsBuiltin(agent.getIsBuiltin());
        vo.setRemark(agent.getRemark());
        return vo;
    }

    private AiAgentRunVo toRunVo(OaAiAgentRun run, OaAiAgent agent, boolean withTrace) {
        AiAgentRunVo vo = new AiAgentRunVo();
        vo.setId(run.getId());
        vo.setAgentId(run.getAgentId());
        vo.setAgentName(agent == null ? null : agent.getName());
        vo.setUserId(run.getUserId());
        vo.setConversationId(run.getConversationId());
        vo.setInput(run.getInput());
        vo.setOutput(run.getOutput());
        vo.setStatus(run.getStatus());
        vo.setErrorMsg(run.getErrorMsg());
        vo.setTotalTokens(run.getTotalTokens());
        vo.setDurationMs(run.getDurationMs());
        vo.setCreateTime(run.getCreateTime());
        if (withTrace) {
            vo.setTrace(parseTrace(run.getTraceJson()));
        }
        return vo;
    }

    private List<AiAgentTraceStepVo> parseTrace(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JSONArray array = JSONUtil.parseArray(json);
            List<AiAgentTraceStepVo> result = new ArrayList<>();
            for (Object item : array) {
                result.add(JSONUtil.toBean(String.valueOf(item), AiAgentTraceStepVo.class));
            }
            return result;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static String truncate(String message, int max) {
        if (message == null) {
            return null;
        }
        return message.length() <= max ? message : message.substring(0, max);
    }

    /** 异步/测试上下文可能没有登录态，越权判断降级为非管理员 */
    private static boolean superAdmin() {
        try {
            return LoginHelper.isSuperAdmin();
        } catch (RuntimeException e) {
            return false;
        }
    }
}

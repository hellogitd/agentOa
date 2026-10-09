package org.dromara.agentoa.ai.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.OaAiTool;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiToolBo;
import org.dromara.agentoa.ai.domain.vo.AiToolDiscoveryVo;
import org.dromara.agentoa.ai.domain.vo.AiToolTestVo;
import org.dromara.agentoa.ai.domain.vo.AiToolVo;
import org.dromara.agentoa.ai.mapper.OaAiToolMapper;
import org.dromara.agentoa.ai.service.IAiToolService;
import org.dromara.agentoa.ai.service.support.FunctionToolRegistry;
import org.dromara.agentoa.ai.service.support.McpToolGateway;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具与 MCP 管理实现（docs/21 AI-M5-01/02）。
 * <p>
 * MCP 配置中的鉴权头只写不读：列表/详情仅回显连接地址与是否配置了鉴权头。
 */
@Service
@RequiredArgsConstructor
public class AiToolServiceImpl implements IAiToolService {

    private final OaAiToolMapper toolMapper;
    private final FunctionToolRegistry functionRegistry;
    private final McpToolGateway mcpToolGateway;

    @Override
    public PageVo<AiToolVo> page(AiPageQuery page) {
        IPage<OaAiTool> result = toolMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()),
            new LambdaQueryWrapper<OaAiTool>()
                .orderByAsc(OaAiTool::getType)
                .orderByAsc(OaAiTool::getCode));
        List<AiToolVo> records = new ArrayList<>();
        for (OaAiTool tool : result.getRecords()) {
            records.add(toVo(tool));
        }
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public AiToolVo get(Long id) {
        return toVo(require(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiToolVo create(AiToolBo bo) {
        OaAiTool exists = toolMapper.selectOne(new LambdaQueryWrapper<OaAiTool>()
            .eq(OaAiTool::getCode, bo.getCode())
            .last("LIMIT 1"));
        if (exists != null) {
            throw new ServiceException("AI_TOOL_CODE_EXISTS 工具码已存在", 409);
        }
        OaAiTool tool = new OaAiTool();
        apply(tool, bo);
        tool.setIsBuiltin(0);
        toolMapper.insert(tool);
        return toVo(tool);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiToolVo update(AiToolBo bo) {
        OaAiTool tool = require(bo.getId());
        if (tool.getIsBuiltin() != null && tool.getIsBuiltin() == 1) {
            // 内置工具只允许启停，避免误改实现契约
            if (bo.getEnabled() != null) {
                tool.setEnabled(bo.getEnabled() == 1 ? 1 : 0);
            }
            if (bo.getDescription() != null) {
                tool.setDescription(bo.getDescription());
            }
            toolMapper.updateById(tool);
            return toVo(tool);
        }
        if (bo.getCode() != null && !bo.getCode().equals(tool.getCode())) {
            OaAiTool exists = toolMapper.selectOne(new LambdaQueryWrapper<OaAiTool>()
                .eq(OaAiTool::getCode, bo.getCode())
                .last("LIMIT 1"));
            if (exists != null && !exists.getId().equals(tool.getId())) {
                throw new ServiceException("AI_TOOL_CODE_EXISTS 工具码已存在", 409);
            }
        }
        apply(tool, bo);
        toolMapper.updateById(tool);
        return toVo(tool);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OaAiTool tool = require(id);
        if (tool.getIsBuiltin() != null && tool.getIsBuiltin() == 1) {
            throw new ServiceException("AI_TOOL_BUILTIN_LOCKED 内置工具不可删除", 409);
        }
        toolMapper.deleteById(id);
    }

    @Override
    public AiToolTestVo test(Long id) {
        OaAiTool tool = require(id);
        AiToolTestVo vo = new AiToolTestVo();
        vo.setType(tool.getType());
        long start = System.currentTimeMillis();
        try {
            if (OaAiTool.TYPE_MCP.equals(tool.getType())) {
                int count = mcpToolGateway.ping(tool.getConfigJson());
                vo.setOk(true);
                vo.setToolCount(count);
                vo.setMessage("MCP 连接正常，发现 " + count + " 个工具");
            } else {
                boolean ready = functionRegistry.supports(tool.getCode());
                vo.setOk(ready);
                vo.setToolCount(ready ? 1 : 0);
                vo.setMessage(ready ? "函数工具实现就绪" : "缺少对应的 Java 实现");
            }
        } catch (RuntimeException e) {
            vo.setOk(false);
            vo.setToolCount(0);
            vo.setMessage(e.getMessage());
        }
        vo.setLatencyMs(System.currentTimeMillis() - start);
        return vo;
    }

    @Override
    public List<AiToolDiscoveryVo> discover(Long id) {
        OaAiTool tool = require(id);
        if (!OaAiTool.TYPE_MCP.equals(tool.getType())) {
            throw new ServiceException("AI_TOOL_NOT_MCP 仅 MCP 工具支持发现", 400);
        }
        List<AiToolDiscoveryVo> result = new ArrayList<>();
        for (McpToolGateway.DiscoveredTool discovered : mcpToolGateway.discover(tool.getConfigJson())) {
            AiToolDiscoveryVo vo = new AiToolDiscoveryVo();
            vo.setName(discovered.name());
            vo.setDescription(discovered.description());
            vo.setParametersJsonSchema(discovered.parametersJsonSchema());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AiToolVo> importDiscovered(Long id) {
        OaAiTool server = require(id);
        if (!OaAiTool.TYPE_MCP.equals(server.getType())) {
            throw new ServiceException("AI_TOOL_NOT_MCP 仅 MCP 工具支持发现", 400);
        }
        List<AiToolVo> imported = new ArrayList<>();
        for (McpToolGateway.DiscoveredTool discovered : mcpToolGateway.discover(server.getConfigJson())) {
            if (discovered.name() == null || discovered.name().isBlank()) {
                continue;
            }
            OaAiTool exists = toolMapper.selectOne(new LambdaQueryWrapper<OaAiTool>()
                .eq(OaAiTool::getCode, discovered.name())
                .last("LIMIT 1"));
            if (exists != null) {
                continue;
            }
            OaAiTool tool = new OaAiTool();
            tool.setCode(discovered.name());
            tool.setName(discovered.name());
            tool.setType(OaAiTool.TYPE_MCP);
            tool.setDescription(discovered.description());
            tool.setSchemaJson(discovered.parametersJsonSchema());
            // 沿用 server 连接配置，执行时按 code 调用远端同名工具
            tool.setConfigJson(server.getConfigJson());
            tool.setWriteFlag(0);
            tool.setEnabled(1);
            tool.setIsBuiltin(0);
            tool.setRemark("由 MCP server " + server.getCode() + " 导入");
            toolMapper.insert(tool);
            imported.add(toVo(tool));
        }
        return imported;
    }

    // ---------------------------------------------------------------- internals

    private void apply(OaAiTool tool, AiToolBo bo) {
        if (bo.getCode() != null) {
            tool.setCode(bo.getCode());
        }
        if (bo.getName() != null) {
            tool.setName(bo.getName());
        }
        if (bo.getType() != null) {
            tool.setType(bo.getType());
        }
        if (bo.getDescription() != null) {
            tool.setDescription(bo.getDescription());
        }
        if (bo.getSchemaJson() != null) {
            tool.setSchemaJson(bo.getSchemaJson());
        }
        if (bo.getConfigJson() != null) {
            tool.setConfigJson(bo.getConfigJson());
        }
        if (bo.getWriteFlag() != null) {
            tool.setWriteFlag(bo.getWriteFlag() == 1 ? 1 : 0);
        }
        if (bo.getEnabled() != null) {
            tool.setEnabled(bo.getEnabled() == 1 ? 1 : 0);
        }
        if (bo.getRemark() != null) {
            tool.setRemark(bo.getRemark());
        }
    }

    private OaAiTool require(Long id) {
        OaAiTool tool = toolMapper.selectById(id);
        if (tool == null) {
            throw new ServiceException("AI_TOOL_NOT_FOUND 工具不存在", 404);
        }
        return tool;
    }

    /** 出参脱敏：MCP 鉴权头不回显 */
    private AiToolVo toVo(OaAiTool tool) {
        AiToolVo vo = new AiToolVo();
        vo.setId(tool.getId());
        vo.setCode(tool.getCode());
        vo.setName(tool.getName());
        vo.setType(tool.getType());
        vo.setDescription(tool.getDescription());
        vo.setSchemaJson(tool.getSchemaJson());
        vo.setWriteFlag(tool.getWriteFlag());
        vo.setEnabled(tool.getEnabled());
        vo.setIsBuiltin(tool.getIsBuiltin());
        vo.setRemark(tool.getRemark());
        if (OaAiTool.TYPE_MCP.equals(tool.getType())) {
            JSONObject config = parseConfig(tool.getConfigJson());
            vo.setEndpoint(config.getStr("url") == null
                ? ("stdio:" + config.getStr("command")) : config.getStr("url"));
            vo.setHasAuthHeader(config.getJSONObject("headers") != null
                && !config.getJSONObject("headers").isEmpty());
        }
        return vo;
    }

    private JSONObject parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(configJson);
        } catch (RuntimeException e) {
            return new JSONObject();
        }
    }
}

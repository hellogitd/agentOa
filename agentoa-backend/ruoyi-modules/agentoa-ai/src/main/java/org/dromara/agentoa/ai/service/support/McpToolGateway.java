package org.dromara.agentoa.ai.service.support;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP server 网关（docs/21 AI-M5-02）：基于 LangChain4j MCP client 发现并调用外部工具。
 * <p>
 * 连接配置（{@code oa_ai_tool.config_json}）形如
 * {@code {"transport":"http","url":"...","headers":{...}}} 或
 * {@code {"transport":"stdio","command":["npx","-y","..."]}}。
 * 鉴权头只用于连接，不回显、不落日志（docs/21 §2.3）。
 */
@Component
public class McpToolGateway {

    /** 单次工具发现/调用超时 */
    public static final Duration TOOL_TIMEOUT = Duration.ofSeconds(30);

    /** 发现的 MCP 工具（名称 + 描述 + 参数 Schema） */
    public record DiscoveredTool(String name, String description, String parametersJsonSchema) {
    }

    /** 发现外部 MCP server 上的工具列表 */
    public List<DiscoveredTool> discover(String configJson) {
        McpClient client = open(configJson);
        try {
            List<DiscoveredTool> result = new ArrayList<>();
            for (ToolSpecification spec : client.listTools()) {
                result.add(new DiscoveredTool(spec.name(), spec.description(),
                    spec.parameters() == null ? "{}" : JSONUtil.toJsonStr(spec.parameters())));
            }
            return result;
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceException("AI_MCP_CONNECT_FAILED MCP server 连接失败：" + safeMessage(e), 502);
        } finally {
            closeQuietly(client);
        }
    }

    /** 调用外部 MCP 工具，返回文本结果 */
    public String execute(String configJson, String toolName, String argumentsJson) {
        McpClient client = open(configJson);
        try {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("agentoa-" + System.nanoTime())
                .name(toolName)
                .arguments(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson)
                .build();
            ToolExecutionResult result = client.executeTool(request);
            String text = result == null ? "" : result.resultText();
            return text == null ? "" : text;
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceException("AI_MCP_CALL_FAILED MCP 工具调用失败：" + safeMessage(e), 502);
        } finally {
            closeQuietly(client);
        }
    }

    /** 连通性测试：返回发现到的工具数 */
    public int ping(String configJson) {
        return discover(configJson).size();
    }

    // ---------------------------------------------------------------- internals

    private McpClient open(String configJson) {
        JSONObject config = parseConfig(configJson);
        String transport = config.getStr("transport", "http");
        try {
            McpTransport mcpTransport;
            if ("stdio".equalsIgnoreCase(transport)) {
                List<String> command = new ArrayList<>();
                for (Object item : config.getJSONArray("command")) {
                    command.add(String.valueOf(item));
                }
                if (command.isEmpty()) {
                    throw new ServiceException("AI_MCP_CONFIG_INVALID stdio 连接缺少 command", 400);
                }
                mcpTransport = StdioMcpTransport.builder()
                    .command(command)
                    .logEvents(false)
                    .build();
            } else {
                String url = config.getStr("url");
                if (url == null || url.isBlank()) {
                    throw new ServiceException("AI_MCP_CONFIG_INVALID http 连接缺少 url", 400);
                }
                StreamableHttpMcpTransport.Builder builder = StreamableHttpMcpTransport.builder()
                    .url(url)
                    .logRequests(false)
                    .logResponses(false);
                Map<String, String> headers = headersOf(config);
                if (!headers.isEmpty()) {
                    builder.customHeaders(headers);
                }
                mcpTransport = builder.build();
            }
            return DefaultMcpClient.builder()
                .key("agentoa-mcp-" + System.nanoTime())
                .clientName("agentoa")
                .clientVersion("1.0")
                .transport(mcpTransport)
                .initializationTimeout(TOOL_TIMEOUT)
                .toolExecutionTimeout(TOOL_TIMEOUT)
                .toolExecutionTimeoutErrorMessage("MCP 工具执行超时")
                .autoHealthCheck(false)
                .cacheToolList(false)
                .build();
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceException("AI_MCP_CONNECT_FAILED MCP server 连接失败：" + safeMessage(e), 502);
        }
    }

    /** 只取 headers 字段用于连接，永不打印 */
    private Map<String, String> headersOf(JSONObject config) {
        Map<String, String> result = new LinkedHashMap<>();
        JSONObject headers = config.getJSONObject("headers");
        if (headers != null) {
            for (String key : headers.keySet()) {
                String value = headers.getStr(key);
                if (value != null && !value.isBlank()) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    private JSONObject parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(configJson);
        } catch (RuntimeException e) {
            throw new ServiceException("AI_MCP_CONFIG_INVALID MCP 配置不是合法 JSON", 400);
        }
    }

    /** 关闭客户端，异常不影响主流程 */
    private void closeQuietly(McpClient client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception ignored) {
            // 连接关闭失败不阻断结果返回
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }
}

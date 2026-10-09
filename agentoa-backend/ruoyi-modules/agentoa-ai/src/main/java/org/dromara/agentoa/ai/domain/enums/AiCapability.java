package org.dromara.agentoa.ai.domain.enums;

import cn.hutool.json.JSONUtil;
import org.dromara.common.core.exception.ServiceException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 模型能力（docs/21 §2.4 字典 ai_model_capability）。
 */
public enum AiCapability {

    CHAT("chat"),
    VISION("vision"),
    EMBEDDING("embedding"),
    RERANK("rerank");

    private final String code;

    AiCapability(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** 解析 capability JSON 数组（如 ["chat","vision"]） */
    public static List<String> parse(String capabilityJson) {
        List<String> result = new ArrayList<>();
        if (capabilityJson == null || capabilityJson.isBlank()) {
            return result;
        }
        String trimmed = capabilityJson.trim();
        if (trimmed.startsWith("[")) {
            for (Object item : JSONUtil.parseArray(trimmed)) {
                if (item != null) {
                    result.add(String.valueOf(item).toLowerCase(Locale.ROOT));
                }
            }
            return result;
        }
        for (String part : trimmed.split(",")) {
            if (!part.isBlank()) {
                result.add(part.trim().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    /** 格式化为 JSON 数组字符串 */
    public static String format(List<String> codes) {
        List<String> normalized = new ArrayList<>();
        if (codes != null) {
            for (String code : codes) {
                if (code != null && !code.isBlank()) {
                    normalized.add(code.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return JSONUtil.toJsonStr(normalized);
    }

    public static boolean supports(String capabilityJson, String code) {
        return parse(capabilityJson).contains(code.toLowerCase(Locale.ROOT));
    }

    /** 仅允许字典内能力取值 */
    public static void validate(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new ServiceException("AI_MODEL_CAPABILITY_INVALID 能力不能为空", 400);
        }
        for (String code : codes) {
            boolean known = false;
            for (AiCapability capability : values()) {
                if (capability.code.equals(code)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                throw new ServiceException("AI_MODEL_CAPABILITY_INVALID 不支持的能力 " + code, 400);
            }
        }
    }
}

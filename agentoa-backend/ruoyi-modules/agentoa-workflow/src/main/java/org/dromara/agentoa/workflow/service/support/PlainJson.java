package org.dromara.agentoa.workflow.service.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 独立 JSON 读写（不依赖 Spring 上下文）：内置模板目录与表单 Schema 校验在单元测试里
 * 也会被调用，因此不能走 {@code JsonUtils}（其静态初始化需要 ApplicationContext）。
 * 行为与 Spring Boot 默认 ObjectMapper 对齐：忽略未知字段，便于向前兼容。
 */
public final class PlainJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private PlainJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    public static <T> T parse(String text, Class<T> type) {
        try {
            return MAPPER.readValue(text, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 解析失败", e);
        }
    }

    public static <T> java.util.List<T> parseList(String text, Class<T> type) {
        try {
            return MAPPER.readValue(text, MAPPER.getTypeFactory().constructCollectionType(java.util.List.class, type));
        } catch (Exception e) {
            throw new IllegalStateException("JSON 列表解析失败", e);
        }
    }
}

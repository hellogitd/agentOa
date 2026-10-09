package org.dromara.agentoa.ai.service.support;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具 JSON Schema → LangChain4j {@code JsonObjectSchema} 映射（docs/21 AI-M5-01）。
 * <p>
 * 只支持工具入参用到的子集（object/string/number/integer/boolean/array/enum），
 * 非法或缺失 Schema 退化为无参对象，避免工具声明把请求打挂。
 */
public final class ToolSchemaMapper {

    private ToolSchemaMapper() {
    }

    /** 解析工具参数 Schema；空/非法时返回空对象 Schema */
    public static JsonObjectSchema toObjectSchema(String jsonSchema) {
        if (jsonSchema == null || jsonSchema.isBlank()) {
            return JsonObjectSchema.builder().build();
        }
        try {
            JSONObject root = JSONUtil.parseObj(jsonSchema);
            return objectSchema(root);
        } catch (RuntimeException e) {
            return JsonObjectSchema.builder().build();
        }
    }

    private static JsonObjectSchema objectSchema(JSONObject node) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        if (node.containsKey("description")) {
            builder.description(node.getStr("description"));
        }
        JSONObject properties = node.getJSONObject("properties");
        if (properties != null) {
            for (String key : properties.keySet()) {
                JSONObject property = properties.getJSONObject(key);
                builder.addProperty(key, property == null ? JsonStringSchema.builder().build() : element(property));
            }
        }
        JSONArray required = node.getJSONArray("required");
        if (required != null && !required.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Object item : required) {
                if (item != null) {
                    names.add(String.valueOf(item));
                }
            }
            if (!names.isEmpty()) {
                builder.required(names);
            }
        }
        return builder.build();
    }

    private static JsonSchemaElement element(JSONObject node) {
        String type = node.getStr("type");
        String description = node.getStr("description");
        JSONArray enumValues = node.getJSONArray("enum");
        if (enumValues != null && !enumValues.isEmpty()) {
            List<String> values = new ArrayList<>();
            for (Object item : enumValues) {
                values.add(String.valueOf(item));
            }
            return JsonEnumSchema.builder().enumValues(values).description(description).build();
        }
        if (type == null) {
            return JsonStringSchema.builder().description(description).build();
        }
        return switch (type) {
            case "object" -> objectSchema(node);
            case "array" -> JsonArraySchema.builder()
                .items(node.getJSONObject("items") == null
                    ? JsonStringSchema.builder().build()
                    : element(node.getJSONObject("items")))
                .description(description).build();
            case "integer" -> JsonIntegerSchema.builder().description(description).build();
            case "number" -> JsonNumberSchema.builder().description(description).build();
            case "boolean" -> JsonBooleanSchema.builder().description(description).build();
            default -> JsonStringSchema.builder().description(description).build();
        };
    }
}

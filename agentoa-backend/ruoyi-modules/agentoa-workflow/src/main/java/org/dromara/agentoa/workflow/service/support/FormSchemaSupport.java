package org.dromara.agentoa.workflow.service.support;

import org.dromara.agentoa.workflow.domain.bo.FormSchemaBo;
import org.dromara.common.core.exception.ServiceException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 表单 Schema 受控校验与序列化：只接受白名单字段类型，禁止脚本/表达式。
 * 流程定义创建/更新与内置模板注册共用同一套规则。
 */
public final class FormSchemaSupport {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    private static final int MAX_FIELDS = 60;
    private static final int MAX_NEST_DEPTH = 2;

    private FormSchemaSupport() {
    }

    /** 校验并序列化为 JSON */
    public static String validateAndSerialize(FormSchemaBo schema) {
        if (schema == null) {
            throw new ServiceException("表单 Schema 不能为空", 400);
        }
        if (schema.getSchemaVersion() == null || schema.getSchemaVersion() != 1) {
            throw new ServiceException("表单 Schema 版本仅支持 1", 400);
        }
        validateFields(schema.getFields(), 0);
        return PlainJson.toJson(schema);
    }

    private static void validateFields(List<FormSchemaBo.FormFieldBo> fields, int depth) {
        if (fields == null || fields.isEmpty()) {
            throw new ServiceException("表单 Schema 必须包含字段", 400);
        }
        if (fields.size() > MAX_FIELDS) {
            throw new ServiceException("表单字段数量超过上限 " + MAX_FIELDS, 400);
        }
        if (depth > MAX_NEST_DEPTH) {
            throw new ServiceException("表单明细嵌套过深", 400);
        }
        for (FormSchemaBo.FormFieldBo field : fields) {
            if (field == null || field.getKey() == null || !KEY_PATTERN.matcher(field.getKey()).matches()) {
                throw new ServiceException("表单字段 Key 非法", 400);
            }
            if (field.getLabel() == null || field.getLabel().isBlank() || field.getLabel().length() > 64) {
                throw new ServiceException("表单字段名称非法: " + field.getKey(), 400);
            }
            if (field.getType() == null || !FormSchemaBo.FIELD_TYPES.contains(field.getType())) {
                throw new ServiceException("表单字段类型非法: " + field.getType() + " (" + field.getKey() + ")", 400);
            }
            if ("select".equals(field.getType())) {
                if (field.getOptions() == null || field.getOptions().isEmpty() || field.getOptions().size() > 50) {
                    throw new ServiceException("下拉字段必须提供 1~50 个选项: " + field.getKey(), 400);
                }
                for (FormSchemaBo.FormOptionBo option : field.getOptions()) {
                    if (option == null || option.getValue() == null || String.valueOf(option.getValue()).length() > 64) {
                        throw new ServiceException("下拉选项非法: " + field.getKey(), 400);
                    }
                }
            } else if (field.getOptions() != null && !field.getOptions().isEmpty()) {
                throw new ServiceException("仅下拉字段允许选项: " + field.getKey(), 400);
            }
            if ("list".equals(field.getType())) {
                validateFields(field.getItemFields(), depth + 1);
            } else if (field.getItemFields() != null && !field.getItemFields().isEmpty()) {
                throw new ServiceException("仅明细字段允许子字段: " + field.getKey(), 400);
            }
        }
    }
}

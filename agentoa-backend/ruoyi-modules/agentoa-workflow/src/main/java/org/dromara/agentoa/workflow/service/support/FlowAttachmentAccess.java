package org.dromara.agentoa.workflow.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 流程表单附件引用判定（docs/23 H5-H3-01）：附件字段值为 sys_file ID（字符串或数字），
 * 下载授权 = 实例可见性（由 selectDetail 校验）+ 表单数据实际引用该文件，防止 ID 枚举越权下载。
 */
public final class FlowAttachmentAccess {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FlowAttachmentAccess() {
    }

    /** formData JSON 的任意标量值等于该文件 ID 即视为引用（覆盖顶层字段与明细行嵌套）。 */
    public static boolean referencedInForm(Long fileId, String formDataJson) {
        if (fileId == null || formDataJson == null || formDataJson.isBlank()) {
            return false;
        }
        String expected = Long.toString(fileId);
        try {
            JsonNode root = MAPPER.readTree(formDataJson);
            return containsScalar(root, expected);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean containsScalar(JsonNode node, String expected) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isValueNode()) {
            return expected.equals(node.asText());
        }
        for (JsonNode child : node) {
            if (containsScalar(child, expected)) {
                return true;
            }
        }
        return false;
    }
}

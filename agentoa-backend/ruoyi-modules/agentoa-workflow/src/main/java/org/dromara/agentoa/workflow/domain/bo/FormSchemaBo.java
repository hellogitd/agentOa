package org.dromara.agentoa.workflow.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 表单 JSON Schema 结构（docs/05 section 4.2），与前端 FormSchema 类型一一对应。
 * 只接受受控字段类型，禁止任意脚本/表达式。
 */
@Data
public class FormSchemaBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 受控字段类型白名单（file/image 为移动端附件字段，值为 sys_file ID 字符串，docs/23 H5-H3-01） */
    public static final List<String> FIELD_TYPES =
        List.of("input", "textarea", "number", "select", "date", "datetime", "list", "file", "image");

    private Integer schemaVersion = 1;

    private List<FormFieldBo> fields = new ArrayList<>();

    @Data
    public static class FormFieldBo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 字段标识：字母开头，仅 [A-Za-z0-9_] */
        private String key;

        private String label;

        /** input/textarea/number/select/date/datetime/list/file/image */
        private String type;

        private Boolean required;

        private Boolean readonly;

        private List<FormOptionBo> options = new ArrayList<>();

        /** type=list 时的明细字段 */
        private List<FormFieldBo> itemFields = new ArrayList<>();
    }

    @Data
    public static class FormOptionBo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String label;

        private String value;
    }
}

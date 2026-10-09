package org.dromara.agentoa.workflow.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 表单设计载荷：随流程定义一起提交（PUT/POST /api/v1/wf/definitions）。
 */
@Data
public class FormDesignBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 表单 Key（字母开头，仅 [A-Za-z0-9_]） */
    private String formKey;

    private String formName;

    private FormSchemaBo schema;
}

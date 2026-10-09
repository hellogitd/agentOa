package org.dromara.agentoa.workflow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class FormVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String formKey;

    private String formName;

    private Integer versionNo;

    private String status;

    /** 表单 JSON Schema（schemaVersion + fields） */
    private String schema;
}

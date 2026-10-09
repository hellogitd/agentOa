package org.dromara.agentoa.workflow.domain.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 流程定义启停用载荷：PUBLISHED=启用（可发起）、RETIRED=停用（不可发起，在途实例继续）、DRAFT=草稿。
 */
@Data
public class DefinitionStatusBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "status 不能为空")
    private String status;
}

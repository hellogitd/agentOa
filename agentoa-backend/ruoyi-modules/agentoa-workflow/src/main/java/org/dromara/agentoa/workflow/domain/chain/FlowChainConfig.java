package org.dromara.agentoa.workflow.domain.chain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构化审批链配置（唯一允许的流程结构输入，不开放 BPMN 上传）。
 * 由 {@code FlowChainCompiler} 编译为受控 BPMN，并复用 {@code BpmnTemplateValidator} 做编译门禁。
 */
@Data
public class FlowChainConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 配置结构版本 */
    private Integer chainVersion = 1;

    /** 顶层顺序节点 */
    private List<FlowChainNode> nodes = new ArrayList<>();
}

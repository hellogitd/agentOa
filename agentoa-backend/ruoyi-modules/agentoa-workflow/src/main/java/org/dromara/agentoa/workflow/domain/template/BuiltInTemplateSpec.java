package org.dromara.agentoa.workflow.domain.template;

import lombok.Data;
import org.dromara.agentoa.workflow.domain.bo.FormSchemaBo;
import org.dromara.agentoa.workflow.domain.chain.FlowChainConfig;

import java.io.Serial;
import java.io.Serializable;

/**
 * 内置流程模板规格：表单设计 + 推荐默认审批链。
 * <p>
 * 内置模板随代码仓库交付，注册时由 {@code FlowChainCompiler} 编译为受控 BPMN，
 * 与用户在向导里创建的自定义流程走完全相同的编译与校验路径。
 */
@Data
public class BuiltInTemplateSpec implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程 Key（唯一） */
    private String processKey;

    private String processName;

    private String formKey;

    private String formName;

    /** 业务类型标识（同 processKey，保证定义查找唯一） */
    private String businessType;

    /** 分类 ID：100 人事 / 101 财务 / 102 考勤 / 103 其他 / 104 行政 */
    private Long categoryId;

    private String icon;

    private Integer sort;

    private String remark;

    /** 表单设计 */
    private FormSchemaBo form;

    /** 推荐默认审批链 */
    private FlowChainConfig chain;
}

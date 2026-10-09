package org.dromara.agentoa.workflow.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.agentoa.workflow.domain.chain.FlowChainConfig;

import java.io.Serial;
import java.io.Serializable;

/**
 * 流程定义创建/更新载荷。
 * <p>
 * 创建（POST）时 {@code processKey} 必填；更新（PUT）时 {@code processKey} 不可变更。
 * {@code form}/{@code chain} 任一变化都会产出新的定义版本（版本发布后不可变）。
 */
@Data
public class DefinitionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程 Key（仅创建时生效） */
    @Size(max = 64, message = "流程 Key 长度不能超过{max}个字符")
    private String processKey;

    @Size(max = 64, message = "流程名称长度不能超过{max}个字符")
    private String processName;

    private Long categoryId;

    /** 业务类型标识，默认 generic */
    @Size(max = 32, message = "业务类型长度不能超过{max}个字符")
    private String businessType;

    @Size(max = 64, message = "图标长度不能超过{max}个字符")
    private String icon;

    private Integer sort;

    @Size(max = 500, message = "备注长度不能超过{max}个字符")
    private String remark;

    /** 表单设计 */
    private FormDesignBo form;

    /** 结构化审批链配置（唯一流程结构输入） */
    private FlowChainConfig chain;
}

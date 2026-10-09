package org.dromara.agentoa.workflow.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 发起申请目录条目（H5/移动端）：只暴露发起所需元数据与已发布表单快照。
 */
@Data
public class LaunchDefinitionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String processKey;

    private String processName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    private String categoryName;

    private String formKey;

    private String businessType;

    private Integer currentVersionNo;

    private String icon;

    private Integer sort;

    private String remark;

    /** 已发布表单快照；表单未发布时为空 */
    private FormVo form;

    /**
     * 发起人自选审批节点（assigneeRule=USER_SELECT）：发起页按此渲染选人行。
     * 只暴露 nodeId/名称/是否多选，不下发完整审批链。
     */
    private List<SelectableNodeVo> selectableNodes = new ArrayList<>();
}

package org.dromara.agentoa.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("oa_flow_definition_version")
public class OaFlowDefinitionVersion {

    @TableId(value = "id")
    private Long id;

    private Long definitionId;

    private Integer versionNo;

    private String bpmnResource;

    /** 编译产物 BPMN（结构化审批链编译结果）；代码仓库模板为空、走 bpmnResource */
    private String bpmnXml;

    /** 结构化审批链配置 JSON（含 cc 节点，实例启动时生成抄送记录） */
    private String chainJson;

    private String bpmnDigest;

    private String validationSummary;

    /** DRAFT/PUBLISHED/RETIRED */
    private String status;

    private String flowableProcDefId;

    private String flowableDeploymentId;

    private Long publishedBy;

    private Date publishedTime;

    private Date createTime;

    private Date updateTime;
}

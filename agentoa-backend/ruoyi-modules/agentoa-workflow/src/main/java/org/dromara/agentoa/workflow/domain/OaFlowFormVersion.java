package org.dromara.agentoa.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("oa_flow_form_version")
public class OaFlowFormVersion {

    @TableId(value = "id")
    private Long id;

    private String formKey;

    private String formName;

    private Integer versionNo;

    private String schemaJson;

    private String schemaDigest;

    /** DRAFT/PUBLISHED/RETIRED */
    private String status;

    private Date createTime;

    private Date updateTime;
}

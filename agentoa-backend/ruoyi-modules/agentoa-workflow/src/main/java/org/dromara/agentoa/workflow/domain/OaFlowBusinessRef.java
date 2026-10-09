package org.dromara.agentoa.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("oa_flow_business_ref")
public class OaFlowBusinessRef {

    @TableId(value = "id")
    private Long id;

    private String businessType;

    private Long businessId;

    private Long instanceId;

    private Integer submissionNo;

    private Date createTime;
}

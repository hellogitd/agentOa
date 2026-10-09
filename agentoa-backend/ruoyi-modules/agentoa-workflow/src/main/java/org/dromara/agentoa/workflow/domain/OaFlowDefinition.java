package org.dromara.agentoa.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_flow_definition")
public class OaFlowDefinition extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private String processKey;

    private String processName;

    private Long categoryId;

    private String formKey;

    /** leave/overtime/correction/reimburse/regularize/offboard */
    private String businessType;

    private Integer currentVersionNo;

    /** DRAFT/PUBLISHED/RETIRED */
    private String status;

    private String icon;

    private Integer sort;

    private String remark;
}

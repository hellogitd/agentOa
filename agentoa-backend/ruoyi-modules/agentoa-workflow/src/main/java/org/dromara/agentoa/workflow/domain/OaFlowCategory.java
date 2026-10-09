package org.dromara.agentoa.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_flow_category")
public class OaFlowCategory extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private String code;

    private String name;

    private Integer sort;

    /** 0 正常 1 停用 */
    private String status;

    private String remark;
}

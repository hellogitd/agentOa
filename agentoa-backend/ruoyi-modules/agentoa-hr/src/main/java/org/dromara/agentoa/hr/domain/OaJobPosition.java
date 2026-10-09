package org.dromara.agentoa.hr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * 业务岗位 oa_job_position，对应 API 资源 /api/v1/hr/posts。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_job_position")
public class OaJobPosition extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private String positionCode;

    private String positionName;

    private String positionLevel;

    private Integer positionSort;

    /** 0正常 1停用 */
    private String status;

    private String remark;
}

package org.dromara.agentoa.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_lifecycle_request")
public class OaLifecycleRequest extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private Long employeeId;

    private Long userId;

    /** REGULARIZE/OFFBOARD */
    private String requestType;

    private LocalDate effectiveDate;

    private String formData;

    /** 1草稿 2审批中 3已通过 4已拒绝 7已撤销 */
    private Integer status;

    private Long flowInstanceId;

    private Integer submissionNo;

    private Integer lockVersion;

    private String remark;
}

package org.dromara.agentoa.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_correction_request")
public class OaCorrectionRequest extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private Long userId;

    private Long employeeId;

    private LocalDate attendanceDate;

    /** 1上班 2下班 */
    private Integer punchType;

    private LocalDateTime correctedTime;

    private String reason;

    /** 1草稿 2审批中 3已通过 4已拒绝 7已撤销 */
    private Integer status;

    private Long flowInstanceId;

    private Integer submissionNo;

    private Integer lockVersion;

    private String remark;
}

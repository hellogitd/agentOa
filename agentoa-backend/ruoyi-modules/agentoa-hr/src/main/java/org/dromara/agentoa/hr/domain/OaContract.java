package org.dromara.agentoa.hr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.time.LocalDate;

/**
 * 员工合同 oa_contract（P1，需求 HR-09）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_contract")
public class OaContract extends TenantEntity {

    /** 固定期限 */
    public static final String TYPE_FIXED_TERM = "FIXED_TERM";
    /** 无固定期限 */
    public static final String TYPE_OPEN_ENDED = "OPEN_ENDED";
    /** 以完成一定工作任务 */
    public static final String TYPE_TASK_BASED = "TASK_BASED";

    /** 生效 */
    public static final int STATUS_ACTIVE = 1;
    /** 到期 */
    public static final int STATUS_EXPIRED = 2;
    /** 终止 */
    public static final int STATUS_TERMINATED = 3;

    @TableId(value = "id")
    private Long id;

    private Long employeeId;

    private String contractNo;

    /** 合同类型 FIXED_TERM/OPEN_ENDED/TASK_BASED */
    private String contractType;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalDate signDate;

    /** 续签次数 */
    private Integer renewCount;

    /** 状态（1生效 2到期 3终止） */
    private Integer status;

    /** 合同扫描件文件 ID */
    private Long fileId;

    private String remark;
}

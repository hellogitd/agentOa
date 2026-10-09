package org.dromara.agentoa.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.time.LocalDate;

/**
 * 流程委托代理 oa_flow_delegate（P1，需求 WF-12，API 规范 4.5）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_flow_delegate")
public class OaFlowDelegate extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    /** 委托人账号 ID */
    private Long ownerId;

    /** 受托人账号 ID */
    private Long delegateId;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 适用流程（逗号分隔，空=全部） */
    private String processKeys;

    /** 状态（1启用 0停用） */
    private Integer status;

    private String remark;
}

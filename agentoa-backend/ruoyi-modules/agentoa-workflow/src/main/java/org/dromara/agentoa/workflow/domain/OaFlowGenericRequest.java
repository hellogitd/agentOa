package org.dromara.agentoa.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * 通用 OA 申请承接单（M5）：纯 OA 表单/自定义流程的业务载体。
 * 表单字段差异全部收敛在 form_data（JSON），流程结构由定义上的结构化审批链决定。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_flow_generic_request")
public class OaFlowGenericRequest extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    /** 目标流程定义 ID */
    private Long definitionId;

    private Long userId;

    private String title;

    /** 表单数据 JSON（按定义的表单 Schema 填写） */
    private String formData;

    /** 1草稿 2审批中 3已通过 4已拒绝 7已撤销 */
    private Integer status;

    private Long flowInstanceId;

    private Integer submissionNo;

    private Integer lockVersion;

    private String remark;
}

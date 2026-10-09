package org.dromara.agentoa.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * 流程抄送记录 oa_flow_cc（P1，API 规范 4.4 /tasks/cc）。event_id 幂等保证一次抄送只投递一次。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_flow_cc")
public class OaFlowCc extends TenantEntity {

    @TableId(value = "id")
    private Long id;

    private Long instanceId;

    private Long senderUserId;

    private Long ccUserId;

    private String comment;

    /** 业务事件 ID（幂等） */
    private String eventId;

    private Date readTime;

    private Date createTime;
}

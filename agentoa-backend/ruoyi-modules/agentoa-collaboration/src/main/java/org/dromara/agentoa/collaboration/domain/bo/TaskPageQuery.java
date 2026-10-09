package org.dromara.agentoa.collaboration.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 任务列表查询（docs/05 9.3） */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskPageQuery extends CollabPageQuery {

    /** 状态过滤 */
    private Integer status;

    /** 负责人过滤（默认不限，服务端按可见范围过滤） */
    private String assigneeId;

    private String keyword;

    /** self=我负责/参与（默认）；created=我指派的 */
    private String scope;
}

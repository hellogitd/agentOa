package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 工作台流程条目（待办/我发起的）。 */
@Data
public class WorkItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    private String title;

    private String businessType;

    private String status;

    private String time;
}

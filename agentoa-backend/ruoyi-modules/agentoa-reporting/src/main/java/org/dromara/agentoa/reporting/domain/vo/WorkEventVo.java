package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 工作台今日日程条目。 */
@Data
public class WorkEventVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    private String title;

    private String startTime;

    private String endTime;
}

package org.dromara.agentoa.reporting.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/** 异常列表项（超时流程、异常考勤等）。 */
@Data
public class ExceptionItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    private String label;

    private String detail;

    private String time;
}

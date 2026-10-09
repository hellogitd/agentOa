package org.dromara.agentoa.reporting.domain.bo;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 报表区间查询（docs/02 13.4）：起止日为自然日闭区间，服务端按左闭右开时间区间换算；
 * 未传时按默认区间（本月/近 N 天）处理。
 */
@Data
public class ReportRangeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 可选部门过滤（在数据范围内再收窄）。 */
    private Long deptId;

    /** 超时阈值（小时），仅流程看板使用；默认取口径版本定义的 24 小时。 */
    private Integer timeoutHours;
}

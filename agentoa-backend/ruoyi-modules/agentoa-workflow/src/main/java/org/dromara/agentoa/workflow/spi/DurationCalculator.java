package org.dromara.agentoa.workflow.spi;

import java.time.LocalDateTime;

/**
 * 业务时长计算器（docs/13 核心规则）：请假/加班时长由服务端按工作日历、班次和休息段计算，
 * 不接受客户端计算结果。返回 null 表示本次不参与计算（例如用户未配置考勤组），由调用方回退默认算法。
 */
public interface DurationCalculator {

    Integer workMinutes(Long userId, LocalDateTime start, LocalDateTime end);
}

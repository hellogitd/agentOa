package org.dromara.agentoa.workflow.service.support;

import org.dromara.agentoa.workflow.spi.DurationCalculator;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 时长计算器注册表：考勤模块注册后按工作日历/班次/休息段计算；未命中时回退
 * {@link RequestSupport#durationMinutes(LocalDateTime, LocalDateTime)} 的自然时长算法。
 */
@Component
public class DurationCalculators {

    private final List<DurationCalculator> calculators = new CopyOnWriteArrayList<>();

    public synchronized void register(DurationCalculator calculator) {
        if (calculator != null && !calculators.contains(calculator)) {
            calculators.add(calculator);
        }
    }

    public int minutes(Long userId, LocalDateTime start, LocalDateTime end) {
        for (DurationCalculator calculator : calculators) {
            Integer minutes = calculator.workMinutes(userId, start, end);
            if (minutes != null) {
                if (minutes <= 0) {
                    throw new org.dromara.common.core.exception.ServiceException("有效时长必须大于 0", 400);
                }
                return minutes;
            }
        }
        return RequestSupport.durationMinutes(start, end);
    }
}

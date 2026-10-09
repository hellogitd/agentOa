package org.dromara.agentoa.hr.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.mapper.OaEmployeeMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 工号生成：E{yyyyMMdd}{4位当日序号}。数据库唯一约束兜底，唯一键冲突由服务层重试。
 */
@Component
@RequiredArgsConstructor
public class EmployeeNumberGenerator {

    private static final String PREFIX = "E";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final OaEmployeeMapper employeeMapper;

    public String next() {
        String prefix = PREFIX + LocalDate.now().format(DAY);
        String max = employeeMapper.selectMaxEmployeeNo(prefix);
        long seq = 1L;
        if (max != null && max.length() > prefix.length()) {
            try {
                seq = Long.parseLong(max.substring(prefix.length()).replaceFirst("^0+(?!$)", "")) + 1L;
            } catch (NumberFormatException ignored) {
                seq = 1L;
            }
        }
        return prefix + String.format("%04d", seq);
    }

    public static String normalize(String candidate) {
        return candidate == null ? null : candidate.trim().toUpperCase();
    }
}

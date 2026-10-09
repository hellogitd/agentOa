package org.dromara.agentoa.collaboration.service.support;

import org.dromara.common.core.exception.ServiceException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * 时间口径（docs/05 1.6）：时间点为 RFC 3339 带偏移（如 2026-10-02T09:00:00+08:00），
 * 纯业务日期为 yyyy-MM-dd；数据库存 UTC。无偏移输入按 UTC 解释，输出 RFC 3339 UTC。
 */
public final class TimeUtil {

    private static final DateTimeFormatter OUTPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private TimeUtil() {
    }

    /** 解析时间点到 UTC Date；支持 RFC 3339（带/不带偏移）、yyyy-MM-dd HH:mm:ss、yyyy-MM-dd */
    public static Date parseInstant(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new ServiceException("CL_TIME_INVALID " + field + " 不能为空", 400);
        }
        String value = text.trim();
        try {
            if (value.length() == 10) {
                LocalDate date = LocalDate.parse(value, DATE);
                return toDate(date.atStartOfDay());
            }
            try {
                return toDate(OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
            } catch (DateTimeParseException ignored) {
                // 无偏移格式
            }
            String normalized = value.replace(' ', 'T');
            return toDate(LocalDateTime.parse(normalized));
        } catch (DateTimeParseException e) {
            throw new ServiceException("CL_TIME_INVALID " + field + " 时间格式非法", 400);
        }
    }

    /** 解析业务日期（yyyy-MM-dd） */
    public static Date parseDate(String text, String field) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return toDate(LocalDate.parse(text.trim(), DATE).atStartOfDay());
        } catch (DateTimeParseException e) {
            throw new ServiceException("CL_TIME_INVALID " + field + " 日期格式非法", 400);
        }
    }

    /** 输出 RFC 3339 UTC */
    public static String format(Date time) {
        return time == null ? null : OUTPUT.format(toLocalDateTime(time));
    }

    /** 输出业务日期 yyyy-MM-dd */
    public static String formatDate(Date time) {
        return time == null ? null : DATE.format(toLocalDateTime(time).toLocalDate());
    }

    /** 校验时间段：结束必须晚于开始，且不超过 31 天 */
    public static void validateRange(Date start, Date end) {
        if (start == null || end == null) {
            throw new ServiceException("CL_TIME_INVALID 起止时间不能为空", 400);
        }
        if (!end.after(start)) {
            throw new ServiceException("CL_TIME_INVALID 结束时间必须晚于开始时间", 400);
        }
        long days = (end.getTime() - start.getTime()) / (24L * 60 * 60 * 1000);
        if (days > 31) {
            throw new ServiceException("CL_TIME_INVALID 时间跨度不能超过31天", 400);
        }
    }

    private static Date toDate(LocalDateTime time) {
        return Date.from(time.toInstant(ZoneOffset.UTC));
    }

    private static LocalDateTime toLocalDateTime(Date time) {
        return LocalDateTime.ofInstant(time.toInstant(), ZoneOffset.UTC);
    }
}

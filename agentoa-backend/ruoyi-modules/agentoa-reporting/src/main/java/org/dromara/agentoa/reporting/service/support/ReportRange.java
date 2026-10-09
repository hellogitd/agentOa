package org.dromara.agentoa.reporting.service.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报表区间与数值口径工具（docs/02 13.4）：
 * 区间为 [startDate, endDateExclusive)；DATETIME 过滤使用与业务写入一致的存储值。
 */
public final class ReportRange {

    private final LocalDate start;
    private final LocalDate endExclusive;

    private ReportRange(LocalDate start, LocalDate endExclusive) {
        this.start = start;
        this.endExclusive = endExclusive;
    }

    /** 默认区间：最近 30 天（含今天）。 */
    public static ReportRange lastDays(int days) {
        LocalDate end = LocalDate.now().plusDays(1);
        return new ReportRange(end.minusDays(days), end);
    }

    /** 默认区间：本月。 */
    public static ReportRange currentMonth() {
        LocalDate today = LocalDate.now();
        return new ReportRange(today.withDayOfMonth(1), today.plusDays(1));
    }

    /** 默认区间：近 12 个月（含今天，按月对齐）。 */
    public static ReportRange last12Months() {
        LocalDate today = LocalDate.now();
        return new ReportRange(today.withDayOfMonth(1).minusMonths(11), today.plusDays(1));
    }

    /** 起止日为自然日闭区间，展开为左闭右开。 */
    public static ReportRange of(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return currentMonth();
        }
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        LocalDate start = startDate == null ? end.withDayOfMonth(1) : startDate;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        return new ReportRange(start, end.plusDays(1));
    }

    public LocalDate start() {
        return start;
    }

    public LocalDate endExclusive() {
        return endExclusive;
    }

    public LocalDateTime startDateTime() {
        return start.atStartOfDay();
    }

    public LocalDateTime endExclusiveDateTime() {
        return endExclusive.atStartOfDay();
    }

    /** 比率（0-1）保留 4 位小数；分母为零返回 null。 */
    public static String ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP).toPlainString();
    }

    /** 金额（元）保留 2 位小数字符串。 */
    public static String amount(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 分钟折算天（480 分钟/天，docs/18 口径），保留 2 位小数。 */
    public static String minutesToDays(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(480), 2, RoundingMode.HALF_UP).toPlainString();
    }
}

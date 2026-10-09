package org.dromara.agentoa.collaboration.service.support;

import org.dromara.common.core.exception.ServiceException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 受控 RRULE 解析器（CL-01）：仅支持 FREQ=DAILY|WEEKLY|MONTHLY|YEARLY + INTERVAL + BYDAY/BYMONTHDAY + COUNT|UNTIL。
 * 未知指令拒绝（白名单校验，同 BPMN 校验思路）。预物化展开 ≤ 12 个月且 ≤ 365 条。
 */
public final class RecurrenceRule {

    /** 展开上限：12 个月 */
    private static final int MAX_MONTHS = 12;
    /** 展开上限：365 条 */
    private static final int MAX_INSTANCES = 365;

    public final String freq;
    public final int interval;
    public final Set<DayOfWeek> byDay;
    public final Set<Integer> byMonthDay;
    public final Integer count;
    public final LocalDate until;

    private RecurrenceRule(String freq, int interval, Set<DayOfWeek> byDay,
                           Set<Integer> byMonthDay, Integer count, LocalDate until) {
        this.freq = freq;
        this.interval = interval;
        this.byDay = byDay;
        this.byMonthDay = byMonthDay;
        this.count = count;
        this.until = until;
    }

    /** 解析 RRULE 字符串（如 "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE;COUNT=10"） */
    public static RecurrenceRule parse(String rule) {
        if (rule == null || rule.isBlank()) {
            throw new ServiceException("CL_RRULE_INVALID 重复规则不能为空", 400);
        }
        String freq = null;
        int interval = 1;
        Set<DayOfWeek> byDay = new LinkedHashSet<>();
        Set<Integer> byMonthDay = new LinkedHashSet<>();
        Integer count = null;
        LocalDate until = null;

        for (String part : rule.trim().split(";")) {
            String kv = part.trim();
            if (kv.isEmpty()) {
                continue;
            }
            int eq = kv.indexOf('=');
            if (eq <= 0) {
                throw new ServiceException("CL_RRULE_INVALID 重复规则格式错误: " + kv, 400);
            }
            String key = kv.substring(0, eq).trim().toUpperCase();
            String value = kv.substring(eq + 1).trim().toUpperCase();
            switch (key) {
                case "FREQ" -> {
                    if (!Set.of("DAILY", "WEEKLY", "MONTHLY", "YEARLY").contains(value)) {
                        throw new ServiceException("CL_RRULE_FREQ_UNSUPPORTED 不支持的重复频率: " + value, 400);
                    }
                    freq = value;
                }
                case "INTERVAL" -> {
                    try {
                        interval = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new ServiceException("CL_RRULE_INVALID INTERVAL 必须为正整数: " + value, 400);
                    }
                    if (interval < 1 || interval > 365) {
                        throw new ServiceException("CL_RRULE_INVALID INTERVAL 范围 1-365", 400);
                    }
                }
                case "BYDAY" -> {
                    for (String d : value.split(",")) {
                        DayOfWeek dow = parseDayOfWeek(d.trim());
                        if (dow == null) {
                            throw new ServiceException("CL_RRULE_INVALID BYDAY 取值非法: " + d, 400);
                        }
                        byDay.add(dow);
                    }
                }
                case "BYMONTHDAY" -> {
                    for (String m : value.split(",")) {
                        try {
                            int day = Integer.parseInt(m.trim());
                            if (day < 1 || day > 31) {
                                throw new ServiceException("CL_RRULE_INVALID BYMONTHDAY 范围 1-31", 400);
                            }
                            byMonthDay.add(day);
                        } catch (NumberFormatException e) {
                            throw new ServiceException("CL_RRULE_INVALID BYMONTHDAY 取值非法: " + m, 400);
                        }
                    }
                }
                case "COUNT" -> {
                    try {
                        count = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new ServiceException("CL_RRULE_INVALID COUNT 必须为正整数: " + value, 400);
                    }
                    if (count < 1 || count > MAX_INSTANCES) {
                        throw new ServiceException("CL_RRULE_INVALID COUNT 范围 1-" + MAX_INSTANCES, 400);
                    }
                }
                case "UNTIL" -> {
                    until = parseDate(value);
                    if (until == null) {
                        throw new ServiceException("CL_RRULE_INVALID UNTIL 格式非法: " + value, 400);
                    }
                }
                default -> throw new ServiceException("CL_RRULE_UNSUPPORTED 不支持的重复指令: " + key, 400);
            }
        }
        if (freq == null) {
            throw new ServiceException("CL_RRULE_INVALID 缺少 FREQ 指令", 400);
        }
        if (count != null && until != null) {
            throw new ServiceException("CL_RRULE_INVALID COUNT 与 UNTIL 不能同时指定", 400);
        }
        return new RecurrenceRule(freq, interval, byDay, byMonthDay, count, until);
    }

    /** 从起始日期展开实例日期列表（不含起始日），受 MAX_MONTHS/MAX_INSTANCES 限制 */
    public List<LocalDate> expand(LocalDate start, int maxInstances) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate limit = start.plusMonths(MAX_MONTHS);
        int effectiveMax = Math.min(maxInstances, MAX_INSTANCES);
        if (count != null) {
            effectiveMax = Math.min(effectiveMax, count);
        }
        LocalDate current = start;
        int generated = 0;
        while (generated < effectiveMax) {
            current = nextOccurrence(current, start);
            if (current == null) {
                break;
            }
            if (current.isAfter(limit)) {
                break;
            }
            if (until != null && current.isAfter(until)) {
                break;
            }
            dates.add(current);
            generated++;
        }
        return dates;
    }

    private LocalDate nextOccurrence(LocalDate from, LocalDate start) {
        return switch (freq) {
            case "DAILY" -> {
                LocalDate next = from.plusDays(interval);
                yield next;
            }
            case "WEEKLY" -> nextWeekly(from, start);
            case "MONTHLY" -> nextMonthly(from, start);
            case "YEARLY" -> from.plusYears(interval);
            default -> null;
        };
    }

    private LocalDate nextWeekly(LocalDate from, LocalDate start) {
        if (byDay.isEmpty()) {
            return from.plusWeeks(interval);
        }
        LocalDate candidate = from.plusDays(1);
        LocalDate weekLimit = from.plusWeeks(interval);
        while (!candidate.isAfter(weekLimit)) {
            if (byDay.contains(candidate.getDayOfWeek()) && !candidate.isBefore(start)) {
                long weeksBetween = java.time.temporal.ChronoUnit.WEEKS.between(start, candidate);
                if (weeksBetween % interval == 0) {
                    return candidate;
                }
            }
            candidate = candidate.plusDays(1);
        }
        return from.plusWeeks(interval);
    }

    private LocalDate nextMonthly(LocalDate from, LocalDate start) {
        if (byMonthDay.isEmpty()) {
            return from.plusMonths(interval);
        }
        LocalDate candidate = from.plusDays(1);
        LocalDate monthLimit = from.plusMonths(interval + 1);
        while (!candidate.isAfter(monthLimit)) {
            if (byMonthDay.contains(candidate.getDayOfMonth()) && !candidate.isBefore(start)) {
                long monthsBetween = java.time.temporal.ChronoUnit.MONTHS.between(
                    start.withDayOfMonth(1), candidate.withDayOfMonth(1));
                if (monthsBetween % interval == 0) {
                    return candidate;
                }
            }
            candidate = candidate.plusDays(1);
        }
        return from.plusMonths(interval);
    }

    private static DayOfWeek parseDayOfWeek(String s) {
        return switch (s) {
            case "MO" -> DayOfWeek.MONDAY;
            case "TU" -> DayOfWeek.TUESDAY;
            case "WE" -> DayOfWeek.WEDNESDAY;
            case "TH" -> DayOfWeek.THURSDAY;
            case "FR" -> DayOfWeek.FRIDAY;
            case "SA" -> DayOfWeek.SATURDAY;
            case "SU" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            try {
                return java.time.LocalDateTime.parse(s).toLocalDate();
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /** 将 Date 转为本地日期 */
    public static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** 将本地日期转为 Date（UTC 零点） */
    public static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}

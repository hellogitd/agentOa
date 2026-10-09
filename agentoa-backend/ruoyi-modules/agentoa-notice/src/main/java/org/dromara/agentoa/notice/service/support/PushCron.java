package org.dromara.agentoa.notice.service.support;

import org.dromara.common.core.exception.ServiceException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 定时推送 cron 受限子集（NC-04，字典 nc_schedule_type=周期）。
 * <p>
 * 5 字段：分 时 日 月 周；每字段支持 *、*&#47;步长、数字、a-b 区间、逗号列表；
 * 周字段 0-7（0 与 7 均为周日）；日与周同时受限时按标准 cron 语义取“或”。
 * 未知指令（名称、L/W/#、? 等）拒绝，与 BPMN 白名单校验思路一致。
 */
public final class PushCron {

    private static final Pattern FIELD = Pattern.compile("\\*|\\*/\\d+|\\d+|\\d+-\\d+|\\d+/\\d+");
    private static final int MAX_SCAN_DAYS = 370;

    private final Set<Integer> minutes;
    private final Set<Integer> hours;
    private final boolean anyDom;
    private final boolean anyDow;
    private final Set<Integer> doms;
    private final Set<Integer> months;
    private final Set<Integer> dows;

    private PushCron(Set<Integer> minutes, Set<Integer> hours, boolean anyDom, Set<Integer> doms,
                     Set<Integer> months, boolean anyDow, Set<Integer> dows) {
        this.minutes = minutes;
        this.hours = hours;
        this.anyDom = anyDom;
        this.doms = doms;
        this.months = months;
        this.anyDow = anyDow;
        this.dows = dows;
    }

    /** 解析受限子集表达式；非法 400 */
    public static PushCron parse(String expr) {
        if (expr == null || expr.isBlank()) {
            throw new ServiceException("NT_CRON_INVALID 周期表达式不能为空", 400);
        }
        String[] fields = expr.trim().split("\\s+");
        if (fields.length != 5) {
            throw new ServiceException("NT_CRON_INVALID 周期表达式必须为5字段（分 时 日 月 周）", 400);
        }
        Set<Integer> minutes = expand(fields[0], 0, 59, "分钟");
        Set<Integer> hours = expand(fields[1], 0, 23, "小时");
        boolean anyDom = "*".equals(fields[2]);
        Set<Integer> doms = anyDom ? Set.of() : expand(fields[2], 1, 31, "日");
        Set<Integer> months = expand(fields[3], 1, 12, "月");
        boolean anyDow = "*".equals(fields[4]);
        Set<Integer> dows = anyDow ? Set.of() : normalizeDow(expand(fields[4], 0, 7, "周"));
        return new PushCron(minutes, hours, anyDom, doms, months, anyDow, dows);
    }

    /** 下一个匹配时刻（严格晚于 after）；370 天内无匹配返回 null */
    public LocalDateTime nextAfter(LocalDateTime after) {
        LocalDateTime from = after.withSecond(0).withNano(0).plusMinutes(1);
        List<Integer> hourList = new ArrayList<>(hours);
        List<Integer> minuteList = new ArrayList<>(minutes);
        for (int offset = 0; offset <= MAX_SCAN_DAYS; offset++) {
            LocalDateTime day = from.toLocalDate().plusDays(offset).atStartOfDay();
            if (!months.contains(day.getMonthValue()) || !dayMatches(day)) {
                continue;
            }
            for (Integer hour : hourList) {
                for (Integer minute : minuteList) {
                    LocalDateTime candidate = day.withHour(hour).withMinute(minute);
                    if (candidate.isAfter(after.withSecond(0).withNano(0))) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean dayMatches(LocalDateTime day) {
        boolean domMatch = anyDom || doms.contains(day.getDayOfMonth());
        boolean dowMatch = anyDow || dows.contains(day.getDayOfWeek().getValue() % 7);
        if (anyDom && anyDow) {
            return true;
        }
        if (anyDom) {
            return dowMatch;
        }
        if (anyDow) {
            return domMatch;
        }
        return domMatch || dowMatch;
    }

    private static Set<Integer> normalizeDow(Set<Integer> raw) {
        Set<Integer> normalized = new LinkedHashSet<>();
        for (Integer value : raw) {
            normalized.add(value % 7);
        }
        return normalized;
    }

    private static Set<Integer> expand(String field, int min, int max, String label) {
        Set<Integer> values = new LinkedHashSet<>();
        if ("*".equals(field.trim())) {
            for (int v = min; v <= max; v++) {
                values.add(v);
            }
            return Collections.unmodifiableSet(values);
        }
        for (String token : field.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                throw new ServiceException("NT_CRON_INVALID " + label + "字段非法: " + field, 400);
            }
            if (trimmed.startsWith("*/")) {
                int step = positiveStep(trimmed.substring(2), label);
                for (int v = min; v <= max; v += step) {
                    values.add(v);
                }
                continue;
            }
            if (trimmed.contains("-")) {
                String[] range = trimmed.split("-", -1);
                if (range.length != 2) {
                    throw new ServiceException("NT_CRON_INVALID " + label + "字段非法: " + trimmed, 400);
                }
                int from = bound(range[0], min, max, label);
                int to = bound(range[1], min, max, label);
                if (from > to) {
                    throw new ServiceException("NT_CRON_INVALID " + label + "区间非法: " + trimmed, 400);
                }
                for (int v = from; v <= to; v++) {
                    values.add(v);
                }
                continue;
            }
            if (trimmed.contains("/")) {
                String[] parts = trimmed.split("/", -1);
                if (parts.length != 2) {
                    throw new ServiceException("NT_CRON_INVALID " + label + "字段非法: " + trimmed, 400);
                }
                int from = bound(parts[0], min, max, label);
                int step = positiveStep(parts[1], label);
                for (int v = from; v <= max; v += step) {
                    values.add(v);
                }
                continue;
            }
            if (!FIELD.matcher(trimmed).matches()) {
                throw new ServiceException("NT_CRON_INVALID " + label + "字段非法: " + trimmed, 400);
            }
            values.add(bound(trimmed, min, max, label));
        }
        if (values.isEmpty()) {
            throw new ServiceException("NT_CRON_INVALID " + label + "字段不能为空", 400);
        }
        return Collections.unmodifiableSet(values);
    }

    private static int bound(String token, int min, int max, String label) {
        int value;
        try {
            value = Integer.parseInt(token.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("NT_CRON_INVALID " + label + "字段非法: " + token, 400);
        }
        if (value < min || value > max) {
            throw new ServiceException("NT_CRON_INVALID " + label + "取值越界: " + value, 400);
        }
        return value;
    }

    private static int positiveStep(String token, String label) {
        int step = bound(token, 1, 60, label);
        return step;
    }
}

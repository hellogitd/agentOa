package org.dromara.agentoa.workflow.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class RequestSupport {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private RequestSupport() {
    }

    public static void checkOwner(Long ownerUserId) {
        Long current = LoginHelper.getUserId();
        if (LoginHelper.isSuperAdmin() || current.equals(ownerUserId)) {
            return;
        }
        throw new ServiceException("WF_FORBIDDEN 无权访问该申请", 403);
    }

    public static void checkLockVersion(Integer expected, Integer actual) {
        if (expected != null && !expected.equals(actual)) {
            throw new ServiceException("VERSION_CONFLICT 业务版本已改变", 409);
        }
    }

    public static void checkEditable(int status) {
        if (status != 1 && status != 4 && status != 7) {
            throw new ServiceException("WF_STATE_CONFLICT 申请当前状态不可修改", 409);
        }
    }

    public static void checkSubmittable(int status) {
        if (status != 1 && status != 4 && status != 7) {
            throw new ServiceException("WF_STATE_CONFLICT 申请当前状态不可提交", 409);
        }
    }

    public static int durationMinutes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new ServiceException("结束时间必须晚于开始时间", 400);
        }
        long minutes = java.time.Duration.between(start, end).toMinutes();
        if (minutes <= 0) {
            throw new ServiceException("时长必须大于 0", 400);
        }
        return (int) minutes;
    }

    public static String format(LocalDateTime time) {
        return time == null ? null : TIME_FORMAT.format(time);
    }

    public static String format(java.time.LocalDate date) {
        return date == null ? null : date.toString();
    }
}

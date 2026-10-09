package org.dromara.agentoa.reporting.service.support;

/**
 * 敏感字段脱敏（docs/18 测试要求：导出与钻取敏感字段脱敏）。
 * 明文仅在持有 hr:employee:sensitive 之外的敏感展示权限时给出，报表默认脱敏。
 */
public final class ReportMasker {

    private ReportMasker() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        return idCard.substring(0, 4) + "**********" + idCard.substring(idCard.length() - 4);
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int at = email.indexOf('@');
        String head = email.substring(0, at);
        String masked = head.length() <= 1 ? "*" : head.charAt(0) + "***";
        return masked + email.substring(at);
    }
}

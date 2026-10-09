package org.dromara.agentoa.finance.service.support;

import org.dromara.common.core.exception.ServiceException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额对象（docs/14 第 2 步）：API 采用两位小数字符串，服务端转换为最小货币单位（分）整数参与校验。
 */
public final class MoneyUtil {

    private static final String AMOUNT_PATTERN = "^\\d{1,10}(\\.\\d{1,2})?$";

    private MoneyUtil() {
    }

    /** 两位小数字符串 → 分；非法格式、负数、超精度拒绝 */
    public static long toFen(String amount) {
        if (amount == null || !amount.matches(AMOUNT_PATTERN)) {
            throw new ServiceException("FINANCE_AMOUNT_INVALID 金额格式非法: " + amount, 400);
        }
        BigDecimal value = new BigDecimal(amount);
        return value.movePointRight(2).longValueExact();
    }

    /** 落库金额 → 分 */
    public static long toFen(BigDecimal amount) {
        if (amount == null) {
            throw new ServiceException("FINANCE_AMOUNT_INVALID 金额不能为空", 400);
        }
        BigDecimal value = amount.setScale(2, RoundingMode.UNNECESSARY);
        return value.movePointRight(2).longValueExact();
    }

    /** 分 → 两位小数字符串 */
    public static String toAmountString(long fen) {
        return BigDecimal.valueOf(fen, 2).toPlainString();
    }

    /** 分 → 落库金额 */
    public static BigDecimal toAmount(long fen) {
        return BigDecimal.valueOf(fen, 2);
    }
}

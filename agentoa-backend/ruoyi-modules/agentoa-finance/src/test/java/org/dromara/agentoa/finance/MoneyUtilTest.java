package org.dromara.agentoa.finance;

import org.dromara.agentoa.finance.service.support.MoneyUtil;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 金额对象（docs/14 第 2 步）：两位小数字符串与最小货币单位转换。
 */
class MoneyUtilTest {

    @Test
    void convertsTwoDecimalStringsToFen() {
        assertThat(MoneyUtil.toFen("2500.00")).isEqualTo(250000L);
        assertThat(MoneyUtil.toFen("0.05")).isEqualTo(5L);
        assertThat(MoneyUtil.toFen("1000")).isEqualTo(100000L);
        assertThat(MoneyUtil.toFen(new BigDecimal("12.34"))).isEqualTo(1234L);
    }

    @Test
    void rejectsIllegalAmounts() {
        assertThatThrownBy(() -> MoneyUtil.toFen("1.234"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("FINANCE_AMOUNT_INVALID");
        assertThatThrownBy(() -> MoneyUtil.toFen("-1.00"))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> MoneyUtil.toFen("abc"))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> MoneyUtil.toFen((String) null))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> MoneyUtil.toFen((BigDecimal) null))
            .isInstanceOf(ServiceException.class);
    }

    @Test
    void formatsBackToTwoDecimals() {
        assertThat(MoneyUtil.toAmountString(250000L)).isEqualTo("2500.00");
        assertThat(MoneyUtil.toAmount(5L)).isEqualByComparingTo("0.05");
    }
}

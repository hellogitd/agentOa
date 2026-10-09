package org.dromara.agentoa.hr;

import org.dromara.agentoa.hr.service.support.SalaryCipher;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 薪资密文单测：AES-GCM 往返、密钥版本、格式非法。
 */
class SalaryCipherTest {

    @Test
    void encryptDecryptRoundTrip() {
        String cipher = SalaryCipher.encrypt("18000.00");
        assertThat(cipher).startsWith(SalaryCipher.CURRENT_KEY_VERSION + ":");
        assertThat(SalaryCipher.decrypt(cipher)).isEqualTo("18000.00");
        assertThat(SalaryCipher.keyVersionOf(cipher)).isEqualTo(SalaryCipher.CURRENT_KEY_VERSION);
    }

    @Test
    void samePlaintextProducesDistinctCiphertext() {
        assertThat(SalaryCipher.encrypt("100")).isNotEqualTo(SalaryCipher.encrypt("100"));
    }

    @Test
    void invalidFormatRejected() {
        assertThatThrownBy(() -> SalaryCipher.decrypt("bad-format"))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> SalaryCipher.decrypt("1:" + "AAAA"))
            .isInstanceOf(ServiceException.class);
        assertThat(SalaryCipher.decrypt(null)).isNull();
    }
}

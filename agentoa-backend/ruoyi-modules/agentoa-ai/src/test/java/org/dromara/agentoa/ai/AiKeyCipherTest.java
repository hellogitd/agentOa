package org.dromara.agentoa.ai;

import org.dromara.agentoa.ai.service.support.AiKeyCipher;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * API Key 密文单测（docs/21 AI-M1-02）：AES-GCM 往返、脱敏 hint、格式非法。
 */
class AiKeyCipherTest {

    @Test
    void encryptDecryptRoundTrip() {
        String cipher = AiKeyCipher.encrypt("sk-abcdef123456");
        assertThat(cipher).startsWith(AiKeyCipher.CURRENT_KEY_VERSION + ":");
        assertThat(AiKeyCipher.decrypt(cipher)).isEqualTo("sk-abcdef123456");
        assertThat(AiKeyCipher.keyVersionOf(cipher)).isEqualTo(AiKeyCipher.CURRENT_KEY_VERSION);
    }

    @Test
    void samePlaintextProducesDistinctCiphertext() {
        assertThat(AiKeyCipher.encrypt("sk-same")).isNotEqualTo(AiKeyCipher.encrypt("sk-same"));
    }

    @Test
    void hintMasksAllButPrefixAndLast4() {
        String hint = AiKeyCipher.hint("sk-abcdef123456");
        assertThat(hint).isEqualTo("sk-***3456");
        assertThat(hint).doesNotContain("abcdef");
        assertThat(AiKeyCipher.hint("short")).isEqualTo("***");
        assertThat(AiKeyCipher.hint(null)).isNull();
    }

    @Test
    void invalidFormatRejected() {
        assertThatThrownBy(() -> AiKeyCipher.decrypt("bad-format")).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> AiKeyCipher.decrypt("1:AAAA")).isInstanceOf(ServiceException.class);
        assertThat(AiKeyCipher.decrypt(null)).isNull();
    }
}

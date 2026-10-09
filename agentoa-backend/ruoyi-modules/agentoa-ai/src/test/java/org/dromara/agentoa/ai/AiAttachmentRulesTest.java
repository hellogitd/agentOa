package org.dromara.agentoa.ai;

import org.dromara.agentoa.ai.service.support.AiAttachmentRules;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 图片附件规则（docs/21 AI-M2-04）：数量、大小、魔数与扩展名一致。
 */
class AiAttachmentRulesTest {

    @Test
    void verifiesMagicNumbers() {
        byte[] png = new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0};
        byte[] jpg = new byte[]{(byte) 255, (byte) 216, (byte) 255, 0};
        byte[] webp = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0};

        assertThat(AiAttachmentRules.verifiedType("a.png", png)).isEqualTo("image/png");
        assertThat(AiAttachmentRules.verifiedType("a.jpg", jpg)).isEqualTo("image/jpeg");
        assertThat(AiAttachmentRules.verifiedType("a.webp", webp)).isEqualTo("image/webp");
    }

    @Test
    void rejectsMismatchedContent() {
        byte[] notPng = "hello".getBytes();
        assertThatThrownBy(() -> AiAttachmentRules.verifiedType("a.png", notPng))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_ATTACHMENT_TYPE");
        assertThatThrownBy(() -> AiAttachmentRules.verifiedType("a.gif", new byte[]{1, 2, 3, 4}))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_ATTACHMENT_TYPE");
    }

    @Test
    void rejectsOversizeAndTooMany() {
        byte[] huge = new byte[(int) AiAttachmentRules.MAX_IMAGE_BYTES + 1];
        Arrays.fill(huge, (byte) 1);
        assertThatThrownBy(() -> AiAttachmentRules.verifiedType("a.png", huge))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_ATTACHMENT_SIZE");

        assertThatThrownBy(() -> AiAttachmentRules.validateCount(List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("AI_ATTACHMENT_COUNT");
        AiAttachmentRules.validateCount(List.of(1L, 2L, 3L, 4L, 5L));
    }
}

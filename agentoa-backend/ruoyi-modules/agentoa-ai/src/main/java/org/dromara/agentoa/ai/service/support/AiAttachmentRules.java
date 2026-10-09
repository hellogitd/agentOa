package org.dromara.agentoa.ai.service.support;

import org.dromara.common.core.exception.ServiceException;

import java.util.List;
import java.util.Locale;

/**
 * 图片附件规则（docs/21 AI-M2-04）：≤5 张、单张 ≤10 MiB、jpg/png/webp，内容魔数与扩展名一致。
 */
public final class AiAttachmentRules {

    public static final int MAX_IMAGES = 5;
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private AiAttachmentRules() {
    }

    public static void validateCount(List<Long> attachmentIds) {
        if (attachmentIds != null && attachmentIds.size() > MAX_IMAGES) {
            throw new ServiceException("AI_ATTACHMENT_COUNT 图片附件最多 " + MAX_IMAGES + " 张", 400);
        }
    }

    /** 内容、扩展名、MIME 一致才接受 */
    public static String verifiedType(String name, byte[] bytes) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            throw new ServiceException("AI_ATTACHMENT_SIZE 图片必须为 1 字节至 10 MiB", 400);
        }
        if ((lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            && bytes.length >= 3 && bytes[0] == (byte) 255 && bytes[1] == (byte) 216 && bytes[2] == (byte) 255) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png") && bytes.length >= 8
            && bytes[0] == (byte) 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71) {
            return "image/png";
        }
        if (lower.endsWith(".webp") && bytes.length >= 12
            && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
            && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        throw new ServiceException("AI_ATTACHMENT_TYPE 图片内容与扩展名不匹配（仅支持 jpg/png/webp）", 400);
    }
}

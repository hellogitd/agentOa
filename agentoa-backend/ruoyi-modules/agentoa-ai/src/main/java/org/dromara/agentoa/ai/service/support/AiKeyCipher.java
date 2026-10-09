package org.dromara.agentoa.ai.service.support;

import org.dromara.common.core.exception.ServiceException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AI 渠道 API Key 密文工具（docs/21 AI-M1-02）：AES-256-GCM，口径同薪资（docs/03 5.3）。
 * <p>
 * 存储格式 {@code keyVersion:base64(nonce||tag||ciphertext)}；密钥来自环境变量
 * {@code AGENTOA_AI_KEY}（生产必须注入），未配置时使用开发默认密钥（仅限本地/测试）。
 * 明文永不回显、永不落日志，对外只有 {@link #hint(String)}。
 */
public final class AiKeyCipher {

    public static final String CURRENT_KEY_VERSION = "1";

    private static final String DEV_DEFAULT_KEY = "agentoa-dev-ai-key-change-me";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;

    private AiKeyCipher() {
    }

    /** 加密为 keyVersion:base64(nonce||tag||ciphertext) */
    public static String encrypt(String plaintext) {
        return CURRENT_KEY_VERSION + ":" + rawEncrypt(plaintext);
    }

    /** 解密整段密文（含 keyVersion 前缀） */
    public static String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        int sep = stored.indexOf(':');
        if (sep <= 0) {
            throw new ServiceException("AI_KEY_CIPHER_INVALID 密文格式非法", 500);
        }
        return rawDecrypt(stored.substring(sep + 1));
    }

    /** 取密文中的密钥版本 */
    public static String keyVersionOf(String stored) {
        if (stored == null) {
            return null;
        }
        int sep = stored.indexOf(':');
        return sep > 0 ? stored.substring(0, sep) : null;
    }

    /** 脱敏提示：sk-***ab12（仅保留前缀与后 4 位） */
    public static String hint(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        String key = plaintext.trim();
        if (key.length() <= 6) {
            return "***";
        }
        return key.substring(0, 3) + "***" + key.substring(key.length() - 4);
    }

    private static String rawEncrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[nonce.length + ct.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(ct, 0, out, nonce.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new ServiceException("AI_KEY_ENCRYPT_FAILED 密钥加密失败", 500);
        }
    }

    private static String rawDecrypt(String payload) {
        try {
            byte[] all = Base64.getDecoder().decode(payload);
            if (all.length <= NONCE_BYTES) {
                throw new ServiceException("AI_KEY_CIPHER_INVALID 密文格式非法", 500);
            }
            byte[] nonce = new byte[NONCE_BYTES];
            System.arraycopy(all, 0, nonce, 0, NONCE_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] pt = cipher.doFinal(all, NONCE_BYTES, all.length - NONCE_BYTES);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("AI_KEY_DECRYPT_FAILED 密钥解密失败", 500);
        }
    }

    private static SecretKeySpec key() throws Exception {
        String configured = System.getenv("AGENTOA_AI_KEY");
        String source = (configured == null || configured.isBlank()) ? DEV_DEFAULT_KEY : configured;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(source.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }
}

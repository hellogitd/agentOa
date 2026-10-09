package org.dromara.agentoa.hr.service.support;

import org.dromara.common.core.exception.ServiceException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 薪资密文工具（docs/03 5.3：AES-256-GCM，含 nonce/tag 与密钥版本）。
 * <p>
 * 存储格式 {@code keyVersion:base64(nonce||tag||ciphertext)}，薪资永远不进 DECIMAL 列。
 * 密钥来自环境变量 {@code AGENTOA_SALARY_KEY}（生产必须注入），未配置时使用开发默认密钥，
 * 开发默认密钥仅限本地/测试环境使用。
 */
public final class SalaryCipher {

    public static final String CURRENT_KEY_VERSION = "1";

    private static final String DEV_DEFAULT_KEY = "agentoa-dev-salary-key-change-me";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;

    private SalaryCipher() {
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
            throw new ServiceException("薪资密文格式非法", 500);
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

    /** 对外展示脱敏 */
    public static String mask() {
        return "******";
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
            throw new ServiceException("薪资加密失败", 500);
        }
    }

    private static String rawDecrypt(String payload) {
        try {
            byte[] all = Base64.getDecoder().decode(payload);
            if (all.length <= NONCE_BYTES) {
                throw new ServiceException("薪资密文格式非法", 500);
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
            throw new ServiceException("薪资解密失败", 500);
        }
    }

    private static SecretKeySpec key() throws Exception {
        String configured = System.getenv("AGENTOA_SALARY_KEY");
        String source = (configured == null || configured.isBlank()) ? DEV_DEFAULT_KEY : configured;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(source.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }
}

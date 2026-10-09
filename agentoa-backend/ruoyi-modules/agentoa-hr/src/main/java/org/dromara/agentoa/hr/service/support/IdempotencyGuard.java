package org.dromara.agentoa.hr.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.OaIdempotency;
import org.dromara.agentoa.hr.mapper.OaIdempotencyMapper;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

/**
 * Idempotency-Key 支持（API 规范 1.7）：唯一范围为当前用户 + Key，保留 24 小时。
 * 同键同请求返回首次结果；同键不同请求返回 409。
 */
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    private static final long RETENTION_MILLIS = 24L * 60 * 60 * 1000;

    private final OaIdempotencyMapper idempotencyMapper;

    /**
     * 开始一次幂等写入。
     *
     * @return 重放请求返回首次结果引用；首次执行返回 null（业务成功后必须调用 {@link #complete}）
     * @throws ServiceException 409 同键不同请求 / 并发提交
     */
    public String begin(Long userId, String idemKey, String requestPath, String bodyDigest) {
        OaIdempotency existing = idempotencyMapper.selectByUserAndKey(userId, idemKey);
        if (existing != null) {
            if (isExpired(existing)) {
                idempotencyMapper.deleteById(existing.getId());
            } else if (existing.getBodyDigest().equals(bodyDigest)) {
                return existing.getResultRef();
            } else {
                throw new ServiceException("IDEMPOTENCY_CONFLICT 同一 Idempotency-Key 对应了不同请求", 409);
            }
        }
        OaIdempotency record = new OaIdempotency();
        record.setIdemKey(idemKey);
        record.setUserId(userId);
        record.setRequestPath(requestPath);
        record.setBodyDigest(bodyDigest);
        record.setCreateTime(new Date());
        try {
            idempotencyMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("并发的重复请求，请稍后重试", 409);
        }
        return null;
    }

    /** 业务成功后记录结果引用，供重放返回。 */
    public void complete(Long userId, String idemKey, String resultRef) {
        OaIdempotency record = idempotencyMapper.selectByUserAndKey(userId, idemKey);
        if (record != null) {
            record.setResultRef(resultRef);
            idempotencyMapper.updateById(record);
        }
    }

    public static String digest(String body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean isExpired(OaIdempotency record) {
        return record.getCreateTime() == null
            || System.currentTimeMillis() - record.getCreateTime().getTime() > RETENTION_MILLIS;
    }
}

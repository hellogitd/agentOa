package org.dromara.agentoa.workflow.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.OaFlowIdempotency;
import org.dromara.agentoa.workflow.mapper.OaFlowIdempotencyMapper;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

/**
 * 流程写接口 Idempotency-Key 支持（API 规范 1.7）：唯一范围为当前用户 + Key，保留 24 小时。
 */
@Component
@RequiredArgsConstructor
public class FlowIdempotencyGuard {

    private static final long RETENTION_MILLIS = 24L * 60 * 60 * 1000;

    private final OaFlowIdempotencyMapper idempotencyMapper;

    /**
     * @return 重放请求返回首次结果引用；首次执行返回 null（业务成功后必须调用 {@link #complete}）
     * @throws ServiceException 409 同键不同请求 / 并发提交
     */
    public String begin(Long userId, String idemKey, String requestPath, String bodyDigest) {
        OaFlowIdempotency existing = idempotencyMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OaFlowIdempotency>()
                .eq(OaFlowIdempotency::getUserId, userId)
                .eq(OaFlowIdempotency::getIdemKey, idemKey));
        if (existing != null) {
            if (isExpired(existing)) {
                idempotencyMapper.deleteById(existing.getId());
            } else if (existing.getBodyDigest().equals(bodyDigest)) {
                return existing.getResultRef();
            } else {
                throw new ServiceException("IDEMPOTENCY_CONFLICT 同一 Idempotency-Key 对应了不同请求", 409);
            }
        }
        OaFlowIdempotency record = new OaFlowIdempotency();
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

    public void complete(Long userId, String idemKey, String resultRef) {
        OaFlowIdempotency record = idempotencyMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OaFlowIdempotency>()
                .eq(OaFlowIdempotency::getUserId, userId)
                .eq(OaFlowIdempotency::getIdemKey, idemKey));
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

    private boolean isExpired(OaFlowIdempotency record) {
        return record.getCreateTime() == null
            || System.currentTimeMillis() - record.getCreateTime().getTime() > RETENTION_MILLIS;
    }
}

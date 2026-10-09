package org.dromara.agentoa.hr.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.hr.domain.OaIdempotency;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 幂等记录数据层。
 */
public interface OaIdempotencyMapper extends BaseMapperPlus<OaIdempotency, OaIdempotency> {

    default OaIdempotency selectByUserAndKey(Long userId, String idemKey) {
        return this.selectOne(new LambdaQueryWrapper<OaIdempotency>()
            .eq(OaIdempotency::getUserId, userId)
            .eq(OaIdempotency::getIdemKey, idemKey));
    }
}

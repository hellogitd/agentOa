package org.dromara.agentoa.hr.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.hr.domain.OaOnboarding;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 入职事件数据层。
 */
public interface OaOnboardingMapper extends BaseMapperPlus<OaOnboarding, OaOnboarding> {

    default OaOnboarding selectByEmployeeId(Long employeeId) {
        return this.selectOne(new LambdaQueryWrapper<OaOnboarding>().eq(OaOnboarding::getEmployeeId, employeeId));
    }
}

package org.dromara.agentoa.hr.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.hr.domain.OaOffboarding;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 离职事件数据层。
 */
public interface OaOffboardingMapper extends BaseMapperPlus<OaOffboarding, OaOffboarding> {

    default OaOffboarding selectByEmployeeId(Long employeeId) {
        return this.selectOne(new LambdaQueryWrapper<OaOffboarding>().eq(OaOffboarding::getEmployeeId, employeeId));
    }
}

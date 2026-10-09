package org.dromara.agentoa.hr.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.hr.domain.OaEmployeeHistory;
import org.dromara.agentoa.hr.domain.vo.OaEmployeeHistoryVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

/**
 * 员工状态变更历史数据层。
 */
public interface OaEmployeeHistoryMapper extends BaseMapperPlus<OaEmployeeHistory, OaEmployeeHistoryVo> {

    default List<OaEmployeeHistoryVo> selectHistoryByEmployeeId(Long employeeId) {
        return this.selectVoList(new LambdaQueryWrapper<OaEmployeeHistory>()
            .eq(OaEmployeeHistory::getEmployeeId, employeeId)
            .orderByAsc(OaEmployeeHistory::getOperateTime)
            .orderByAsc(OaEmployeeHistory::getId));
    }
}

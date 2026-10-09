package org.dromara.agentoa.hr.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.hr.domain.OaEmployeeWork;
import org.dromara.agentoa.hr.domain.vo.OaWorkVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

/**
 * 工作经历数据层。
 */
public interface OaEmployeeWorkMapper extends BaseMapperPlus<OaEmployeeWork, OaWorkVo> {

    default List<OaWorkVo> selectByEmployeeId(Long employeeId) {
        return this.selectVoList(new LambdaQueryWrapper<OaEmployeeWork>()
            .eq(OaEmployeeWork::getEmployeeId, employeeId)
            .orderByAsc(OaEmployeeWork::getStartDate)
            .orderByAsc(OaEmployeeWork::getId));
    }
}

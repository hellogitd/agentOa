package org.dromara.agentoa.hr.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.hr.domain.OaEmployeeEducation;
import org.dromara.agentoa.hr.domain.vo.OaEducationVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

/**
 * 教育经历数据层。
 */
public interface OaEmployeeEducationMapper extends BaseMapperPlus<OaEmployeeEducation, OaEducationVo> {

    default List<OaEducationVo> selectByEmployeeId(Long employeeId) {
        return this.selectVoList(new LambdaQueryWrapper<OaEmployeeEducation>()
            .eq(OaEmployeeEducation::getEmployeeId, employeeId)
            .orderByAsc(OaEmployeeEducation::getStartDate)
            .orderByAsc(OaEmployeeEducation::getId));
    }
}

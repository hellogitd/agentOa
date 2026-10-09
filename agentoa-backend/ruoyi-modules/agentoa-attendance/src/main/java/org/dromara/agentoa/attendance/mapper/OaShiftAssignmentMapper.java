package org.dromara.agentoa.attendance.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.agentoa.attendance.domain.OaShiftAssignment;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.time.LocalDate;

/**
 * 排班指派数据层。
 */
public interface OaShiftAssignmentMapper extends BaseMapperPlus<OaShiftAssignment, OaShiftAssignment> {

    default OaShiftAssignment selectByUserAndDate(Long userId, LocalDate workDate) {
        return this.selectOne(new LambdaQueryWrapper<OaShiftAssignment>()
            .eq(OaShiftAssignment::getUserId, userId)
            .eq(OaShiftAssignment::getWorkDate, workDate)
            .last("LIMIT 1"));
    }
}

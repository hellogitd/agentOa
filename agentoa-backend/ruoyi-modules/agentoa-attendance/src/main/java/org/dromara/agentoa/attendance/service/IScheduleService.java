package org.dromara.agentoa.attendance.service;

import org.dromara.agentoa.attendance.domain.bo.ScheduleAssignmentBo;
import org.dromara.agentoa.attendance.domain.vo.ScheduleAssignmentVo;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班指派服务（P1，需求 AT-03）。
 */
public interface IScheduleService {

    /** 按人员与日期区间查询排班 */
    List<ScheduleAssignmentVo> selectAssignments(Long userId, LocalDate dateFrom, LocalDate dateTo);

    /** 指派班次：支持单日或日期区间（逐日落一条，重复日期覆盖） */
    List<ScheduleAssignmentVo> assign(ScheduleAssignmentBo bo);

    void deleteAssignment(Long assignmentId);
}

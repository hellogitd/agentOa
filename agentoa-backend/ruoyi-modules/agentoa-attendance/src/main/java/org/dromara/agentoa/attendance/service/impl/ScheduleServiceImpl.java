package org.dromara.agentoa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.OaShift;
import org.dromara.agentoa.attendance.domain.OaShiftAssignment;
import org.dromara.agentoa.attendance.domain.bo.ScheduleAssignmentBo;
import org.dromara.agentoa.attendance.domain.vo.ScheduleAssignmentVo;
import org.dromara.agentoa.attendance.mapper.OaShiftAssignmentMapper;
import org.dromara.agentoa.attendance.mapper.OaShiftMapper;
import org.dromara.agentoa.attendance.service.IScheduleService;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 排班指派服务实现（P1，AT-03）。约束：一人一天一条（唯一键）；
 * 区间指派逐日落库，重复日期覆盖更新；历史日报不重算（规则版本快照，docs/13）。
 */
@RequiredArgsConstructor
@Service
public class ScheduleServiceImpl implements IScheduleService {

    private final OaShiftAssignmentMapper assignmentMapper;
    private final OaShiftMapper shiftMapper;
    private final WorkflowIdentityReadMapper identityMapper;

    @Override
    public List<ScheduleAssignmentVo> selectAssignments(Long userId, LocalDate dateFrom, LocalDate dateTo) {
        LambdaQueryWrapper<OaShiftAssignment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(userId != null, OaShiftAssignment::getUserId, userId)
            .ge(dateFrom != null, OaShiftAssignment::getWorkDate, dateFrom)
            .le(dateTo != null, OaShiftAssignment::getWorkDate, dateTo)
            .orderByAsc(OaShiftAssignment::getWorkDate)
            .orderByAsc(OaShiftAssignment::getId);
        return assignmentMapper.selectList(wrapper).stream().map(this::toVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ScheduleAssignmentVo> assign(ScheduleAssignmentBo bo) {
        if (shiftMapper.selectById(bo.getShiftId()) == null) {
            throw new ServiceException("班次不存在", 404);
        }
        List<LocalDate> dates = resolveDates(bo);
        if (dates.isEmpty()) {
            throw new ServiceException("排班日期不能为空", 400);
        }
        if (dates.size() > 62) {
            throw new ServiceException("单次排班区间不能超过 62 天", 400);
        }
        List<ScheduleAssignmentVo> results = new ArrayList<>();
        for (LocalDate date : dates) {
            OaShiftAssignment existing = assignmentMapper.selectByUserAndDate(bo.getUserId(), date);
            OaShiftAssignment assignment = new OaShiftAssignment();
            assignment.setUserId(bo.getUserId());
            assignment.setShiftId(bo.getShiftId());
            assignment.setWorkDate(date);
            assignment.setSource(bo.getSource() == null ? 1 : bo.getSource());
            assignment.setRemark(bo.getRemark());
            if (existing != null) {
                assignment.setId(existing.getId());
                assignmentMapper.updateById(assignment);
            } else {
                try {
                    assignmentMapper.insert(assignment);
                } catch (DuplicateKeyException e) {
                    throw new ServiceException("同日排班已存在", 409);
                }
            }
            results.add(toVo(assignmentMapper.selectById(assignment.getId())));
        }
        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAssignment(Long assignmentId) {
        OaShiftAssignment assignment = assignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new ServiceException("排班不存在", 404);
        }
        assignmentMapper.deleteById(assignmentId);
    }

    private List<LocalDate> resolveDates(ScheduleAssignmentBo bo) {
        List<LocalDate> dates = new ArrayList<>();
        if (bo.getWorkDate() != null) {
            dates.add(bo.getWorkDate());
            return dates;
        }
        if (bo.getDateFrom() == null || bo.getDateTo() == null) {
            return dates;
        }
        if (bo.getDateTo().isBefore(bo.getDateFrom())) {
            throw new ServiceException("结束日期不能早于开始日期", 400);
        }
        for (LocalDate date = bo.getDateFrom(); !date.isAfter(bo.getDateTo()); date = date.plusDays(1)) {
            dates.add(date);
        }
        return dates;
    }

    private ScheduleAssignmentVo toVo(OaShiftAssignment assignment) {
        ScheduleAssignmentVo vo = new ScheduleAssignmentVo();
        vo.setId(assignment.getId());
        vo.setUserId(assignment.getUserId());
        vo.setUserName(identityMapper.selectNickName(assignment.getUserId()));
        vo.setShiftId(assignment.getShiftId());
        OaShift shift = shiftMapper.selectById(assignment.getShiftId());
        if (shift != null) {
            vo.setShiftName(shift.getShiftName());
        }
        vo.setWorkDate(assignment.getWorkDate());
        vo.setSource(assignment.getSource());
        vo.setRemark(assignment.getRemark());
        return vo;
    }
}

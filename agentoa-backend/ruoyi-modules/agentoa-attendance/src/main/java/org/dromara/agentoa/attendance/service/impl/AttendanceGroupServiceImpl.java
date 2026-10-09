package org.dromara.agentoa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.OaAttendanceGroup;
import org.dromara.agentoa.attendance.domain.OaAttendanceMember;
import org.dromara.agentoa.attendance.domain.OaShift;
import org.dromara.agentoa.attendance.domain.bo.AttendanceGroupBo;
import org.dromara.agentoa.attendance.domain.bo.AttendanceMemberBo;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.vo.AttendanceGroupVo;
import org.dromara.agentoa.attendance.domain.vo.AttendanceMemberVo;
import org.dromara.agentoa.attendance.mapper.OaAttendanceGroupMapper;
import org.dromara.agentoa.attendance.mapper.OaAttendanceMemberMapper;
import org.dromara.agentoa.attendance.mapper.OaShiftMapper;
import org.dromara.agentoa.attendance.service.IAttendanceGroupService;
import org.dromara.agentoa.attendance.service.support.ScheduleResolver;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 考勤组实现：成员生效区间不重叠，删除前检查成员引用；规则生效后的历史日报不重算。
 */
@Service
@RequiredArgsConstructor
public class AttendanceGroupServiceImpl implements IAttendanceGroupService {

    private final OaAttendanceGroupMapper groupMapper;
    private final OaAttendanceMemberMapper memberMapper;
    private final OaShiftMapper shiftMapper;
    private final WorkflowIdentityReadMapper identityMapper;

    @Override
    public PageVo<AttendanceGroupVo> selectPageGroups(AttendanceGroupBo query, AttendancePageQuery page) {
        LambdaQueryWrapper<OaAttendanceGroup> wrapper = new LambdaQueryWrapper<OaAttendanceGroup>()
            .like(StringUtils.isNotBlank(query.getGroupCode()), OaAttendanceGroup::getGroupCode, query.getGroupCode())
            .like(StringUtils.isNotBlank(query.getGroupName()), OaAttendanceGroup::getGroupName, query.getGroupName())
            .eq(query.getShiftId() != null, OaAttendanceGroup::getShiftId, query.getShiftId())
            .eq(StringUtils.isNotBlank(query.getStatus()), OaAttendanceGroup::getStatus, query.getStatus())
            .orderByAsc(OaAttendanceGroup::getGroupCode);
        IPage<OaAttendanceGroup> result = groupMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()), wrapper);
        List<AttendanceGroupVo> records = enrich(result.getRecords());
        return PageVo.of(records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public List<AttendanceGroupVo> selectGroups() {
        return enrich(groupMapper.selectList(new LambdaQueryWrapper<OaAttendanceGroup>()
            .orderByAsc(OaAttendanceGroup::getGroupCode)));
    }

    @Override
    public AttendanceGroupVo selectGroupById(Long id) {
        OaAttendanceGroup group = groupMapper.selectById(id);
        if (group == null) {
            throw new ServiceException("考勤组不存在", 404);
        }
        return toVo(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceGroupVo insertGroup(AttendanceGroupBo bo) {
        checkCodeUnique(bo, null);
        checkWorkDays(bo.getWorkDays());
        requireShift(bo.getShiftId());
        OaAttendanceGroup group = MapstructUtils.convert(bo, OaAttendanceGroup.class);
        groupMapper.insert(group);
        return toVo(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceGroupVo updateGroup(Long id, AttendanceGroupBo bo) {
        OaAttendanceGroup group = groupMapper.selectById(id);
        if (group == null) {
            throw new ServiceException("考勤组不存在", 404);
        }
        checkCodeUnique(bo, id);
        checkWorkDays(bo.getWorkDays());
        requireShift(bo.getShiftId());
        OaAttendanceGroup update = MapstructUtils.convert(bo, OaAttendanceGroup.class);
        update.setId(id);
        groupMapper.updateById(update);
        return toVo(groupMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long id) {
        OaAttendanceGroup group = groupMapper.selectById(id);
        if (group == null) {
            throw new ServiceException("考勤组不存在", 404);
        }
        long members = memberMapper.selectCount(new LambdaQueryWrapper<OaAttendanceMember>()
            .eq(OaAttendanceMember::getGroupId, id));
        if (members > 0) {
            throw new ServiceException("该考勤组仍有成员，不能删除", 409);
        }
        groupMapper.deleteById(id);
    }

    @Override
    public PageVo<AttendanceMemberVo> selectMembers(Long groupId, AttendancePageQuery page) {
        IPage<OaAttendanceMember> result = memberMapper.selectPage(
            new Page<>(page.safePageNum(), page.safePageSize()),
            new LambdaQueryWrapper<OaAttendanceMember>()
                .eq(OaAttendanceMember::getGroupId, groupId)
                .orderByAsc(OaAttendanceMember::getValidFrom));
        List<AttendanceMemberVo> records = MapstructUtils.convert(result.getRecords(), AttendanceMemberVo.class);
        if (records != null) {
            for (AttendanceMemberVo vo : records) {
                vo.setNickname(identityMapper.selectNickName(vo.getUserId()));
            }
        }
        return PageVo.of(records == null ? List.of() : records, result.getTotal(),
            page.safePageNum(), page.safePageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceMemberVo addMember(Long groupId, AttendanceMemberBo bo) {
        OaAttendanceGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new ServiceException("考勤组不存在", 404);
        }
        if (bo.getValidTo() != null && !bo.getValidTo().isAfter(bo.getValidFrom())) {
            throw new ServiceException("失效日期必须晚于生效日期", 400);
        }
        List<OaAttendanceMember> existing = memberMapper.selectList(new LambdaQueryWrapper<OaAttendanceMember>()
            .eq(OaAttendanceMember::getUserId, bo.getUserId()));
        for (OaAttendanceMember other : existing) {
            boolean overlap = !bo.getValidFrom().isAfter(
                other.getValidTo() == null ? java.time.LocalDate.of(9999, 12, 31) : other.getValidTo())
                && (bo.getValidTo() == null || !bo.getValidTo().isBefore(other.getValidFrom()));
            if (overlap) {
                throw new ServiceException("该成员在所选区间已属于其他考勤组", 409);
            }
        }
        OaAttendanceMember member = new OaAttendanceMember();
        member.setGroupId(groupId);
        member.setUserId(bo.getUserId());
        member.setEmployeeId(bo.getEmployeeId() != null ? bo.getEmployeeId() : identityMapper.selectEmployeeId(bo.getUserId()));
        member.setValidFrom(bo.getValidFrom());
        member.setValidTo(bo.getValidTo());
        member.setCreateBy(LoginHelper.getUserId());
        member.setCreateTime(new Date());
        memberMapper.insert(member);
        AttendanceMemberVo vo = MapstructUtils.convert(member, AttendanceMemberVo.class);
        if (vo != null) {
            vo.setNickname(identityMapper.selectNickName(member.getUserId()));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long groupId, Long memberId) {
        OaAttendanceMember member = memberMapper.selectById(memberId);
        if (member == null || !member.getGroupId().equals(groupId)) {
            throw new ServiceException("考勤组成员不存在", 404);
        }
        memberMapper.deleteById(memberId);
    }

    // ------------------------------------------------------------ internal

    private List<AttendanceGroupVo> enrich(List<OaAttendanceGroup> groups) {
        return groups.stream().map(this::toVo).toList();
    }

    private AttendanceGroupVo toVo(OaAttendanceGroup group) {
        AttendanceGroupVo vo = MapstructUtils.convert(group, AttendanceGroupVo.class);
        if (vo != null) {
            OaShift shift = shiftMapper.selectById(group.getShiftId());
            vo.setShiftName(shift == null ? null : shift.getShiftName());
            vo.setMemberCount(memberMapper.selectCount(new LambdaQueryWrapper<OaAttendanceMember>()
                .eq(OaAttendanceMember::getGroupId, group.getId())));
        }
        return vo;
    }

    private void checkCodeUnique(AttendanceGroupBo bo, Long excludeId) {
        long existing = groupMapper.selectCount(new LambdaQueryWrapper<OaAttendanceGroup>()
            .eq(OaAttendanceGroup::getGroupCode, bo.getGroupCode())
            .ne(excludeId != null, OaAttendanceGroup::getId, excludeId));
        if (existing > 0) {
            throw new ServiceException("考勤组编码'" + bo.getGroupCode() + "'已存在", 409);
        }
    }

    private void checkWorkDays(String workDays) {
        if (ScheduleResolver.parseWorkDays(workDays).isEmpty()) {
            throw new ServiceException("工作日配置非法", 400);
        }
    }

    private void requireShift(Long shiftId) {
        if (shiftId == null || shiftMapper.selectById(shiftId) == null) {
            throw new ServiceException("班次不存在", 404);
        }
    }
}

package org.dromara.agentoa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.OaAttendanceGroup;
import org.dromara.agentoa.attendance.domain.OaShift;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.bo.ShiftBo;
import org.dromara.agentoa.attendance.domain.vo.ShiftVo;
import org.dromara.agentoa.attendance.mapper.OaAttendanceGroupMapper;
import org.dromara.agentoa.attendance.mapper.OaShiftMapper;
import org.dromara.agentoa.attendance.service.IShiftService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 班次实现：编码唯一、删除前检查考勤组引用；停用不影响历史日报快照。
 */
@Service
@RequiredArgsConstructor
public class ShiftServiceImpl implements IShiftService {

    private final OaShiftMapper shiftMapper;
    private final OaAttendanceGroupMapper groupMapper;

    @Override
    public PageVo<ShiftVo> selectPageShifts(ShiftBo query, AttendancePageQuery page) {
        LambdaQueryWrapper<OaShift> wrapper = new LambdaQueryWrapper<OaShift>()
            .like(StringUtils.isNotBlank(query.getShiftCode()), OaShift::getShiftCode, query.getShiftCode())
            .like(StringUtils.isNotBlank(query.getShiftName()), OaShift::getShiftName, query.getShiftName())
            .eq(StringUtils.isNotBlank(query.getStatus()), OaShift::getStatus, query.getStatus())
            .orderByAsc(OaShift::getShiftCode);
        IPage<OaShift> result = shiftMapper.selectPage(new Page<>(page.safePageNum(), page.safePageSize()), wrapper);
        List<ShiftVo> records = MapstructUtils.convert(result.getRecords(), ShiftVo.class);
        return PageVo.of(records == null ? List.of() : records, result.getTotal(), page.safePageNum(), page.safePageSize());
    }

    @Override
    public List<ShiftVo> selectShifts() {
        List<ShiftVo> records = MapstructUtils.convert(shiftMapper.selectList(new LambdaQueryWrapper<OaShift>()
            .orderByAsc(OaShift::getShiftCode)), ShiftVo.class);
        return records == null ? List.of() : records;
    }

    @Override
    public ShiftVo selectShiftById(Long id) {
        ShiftVo vo = shiftMapper.selectVoById(id);
        if (vo == null) {
            throw new ServiceException("班次不存在", 404);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftVo insertShift(ShiftBo bo) {
        checkCodeUnique(bo, null);
        checkTimeRange(bo);
        OaShift shift = MapstructUtils.convert(bo, OaShift.class);
        shiftMapper.insert(shift);
        return shiftMapper.selectVoById(shift.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftVo updateShift(Long id, ShiftBo bo) {
        OaShift shift = shiftMapper.selectById(id);
        if (shift == null) {
            throw new ServiceException("班次不存在", 404);
        }
        checkCodeUnique(bo, id);
        checkTimeRange(bo);
        OaShift update = MapstructUtils.convert(bo, OaShift.class);
        update.setId(id);
        shiftMapper.updateById(update);
        return shiftMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteShift(Long id) {
        OaShift shift = shiftMapper.selectById(id);
        if (shift == null) {
            throw new ServiceException("班次不存在", 404);
        }
        long referenced = groupMapper.selectCount(new LambdaQueryWrapper<OaAttendanceGroup>()
            .eq(OaAttendanceGroup::getShiftId, id));
        if (referenced > 0) {
            throw new ServiceException("该班次已被考勤组引用，不能删除", 409);
        }
        shiftMapper.deleteById(id);
    }

    private void checkCodeUnique(ShiftBo bo, Long excludeId) {
        long existing = shiftMapper.selectCount(new LambdaQueryWrapper<OaShift>()
            .eq(OaShift::getShiftCode, bo.getShiftCode())
            .ne(excludeId != null, OaShift::getId, excludeId));
        if (existing > 0) {
            throw new ServiceException("班次编码'" + bo.getShiftCode() + "'已存在", 409);
        }
    }

    private void checkTimeRange(ShiftBo bo) {
        if (bo.getWorkStartTime() == null || bo.getWorkEndTime() == null) {
            throw new ServiceException("上下班时间不能为空", 400);
        }
        if (bo.getWorkStartTime().equals(bo.getWorkEndTime())) {
            throw new ServiceException("上下班时间不能相同", 400);
        }
        if (bo.getRestStartTime() != null && bo.getRestEndTime() != null
            && !bo.getRestEndTime().isAfter(bo.getRestStartTime())) {
            throw new ServiceException("休息结束必须晚于休息开始", 400);
        }
    }
}

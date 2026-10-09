package org.dromara.agentoa.attendance.service;

import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.bo.ShiftBo;
import org.dromara.agentoa.attendance.domain.vo.ShiftVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.List;

/**
 * 班次服务（API 规范 5.2）。
 */
public interface IShiftService {

    PageVo<ShiftVo> selectPageShifts(ShiftBo query, AttendancePageQuery page);

    List<ShiftVo> selectShifts();

    ShiftVo selectShiftById(Long id);

    ShiftVo insertShift(ShiftBo bo);

    ShiftVo updateShift(Long id, ShiftBo bo);

    /** 删除前检查考勤组引用 */
    void deleteShift(Long id);
}

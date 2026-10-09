package org.dromara.agentoa.attendance.service;

import org.dromara.agentoa.attendance.domain.bo.DayQueryBo;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.vo.AttendanceDayVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤日报服务：保存班次/考勤组快照，按原始打卡与请假/加班/补卡结果重算。
 */
public interface IAttendanceDayService {

    /** 重算指定用户指定日期的日报 */
    AttendanceDayVo recompute(Long userId, LocalDate date);

    /** 重算时间区间内每一天（请假/加班/补卡回调使用） */
    void recomputeRange(Long userId, LocalDateTime start, LocalDateTime end);

    AttendanceDayVo selectDay(Long userId, LocalDate date);

    PageVo<AttendanceDayVo> selectPage(DayQueryBo query, AttendancePageQuery page);
}

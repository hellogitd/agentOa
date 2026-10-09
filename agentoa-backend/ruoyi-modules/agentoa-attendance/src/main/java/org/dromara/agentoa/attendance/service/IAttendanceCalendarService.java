package org.dromara.agentoa.attendance.service;

import org.dromara.agentoa.attendance.domain.bo.CalendarBo;
import org.dromara.agentoa.attendance.domain.bo.HolidayBo;
import org.dromara.agentoa.attendance.domain.vo.CalendarVo;
import org.dromara.agentoa.attendance.domain.vo.HolidayVo;

import java.util.List;

/**
 * 工作日历与节假日服务（docs/13 时区与工作日历）。
 */
public interface IAttendanceCalendarService {

    /** year 必填，month 可选 */
    List<CalendarVo> selectCalendar(Integer year, Integer month);

    /** 批量保存日历行（按日期覆盖） */
    List<CalendarVo> saveCalendar(List<CalendarBo> rows);

    List<HolidayVo> selectHolidays(Integer year);

    HolidayVo addHoliday(HolidayBo bo);

    void deleteHoliday(Long id);
}

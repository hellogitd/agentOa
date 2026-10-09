package org.dromara.agentoa.attendance.service;

import org.dromara.agentoa.attendance.domain.bo.DayQueryBo;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.vo.AttendanceDayVo;
import org.dromara.agentoa.attendance.domain.vo.MonthlyReportVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.util.List;

/**
 * 考勤报表服务（API 规范 5.5）：日报可与月报逐日对账，导出遵守与列表同一授权。
 */
public interface IAttendanceReportService {

    PageVo<AttendanceDayVo> daily(DayQueryBo query, AttendancePageQuery page);

    List<MonthlyReportVo> monthly(DayQueryBo query);
}

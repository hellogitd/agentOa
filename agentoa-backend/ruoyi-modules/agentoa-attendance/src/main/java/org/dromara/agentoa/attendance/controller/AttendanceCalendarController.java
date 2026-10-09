package org.dromara.agentoa.attendance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.bo.CalendarBo;
import org.dromara.agentoa.attendance.domain.bo.HolidayBo;
import org.dromara.agentoa.attendance.domain.vo.CalendarVo;
import org.dromara.agentoa.attendance.domain.vo.HolidayVo;
import org.dromara.agentoa.attendance.service.IAttendanceCalendarService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作日历与节假日接口（docs/13）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/attendance/calendar")
public class AttendanceCalendarController {

    private final IAttendanceCalendarService calendarService;

    @SaCheckPermission("at:calendar:list")
    @GetMapping
    public R<List<CalendarVo>> list(@RequestParam Integer year, @RequestParam(required = false) Integer month) {
        return R.ok(calendarService.selectCalendar(year, month));
    }

    @SaCheckPermission("at:calendar:edit")
    @Log(title = "工作日历", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<List<CalendarVo>> save(@Validated @RequestBody List<CalendarBo> rows) {
        return R.ok(calendarService.saveCalendar(rows));
    }

    @SaCheckPermission("at:calendar:list")
    @GetMapping("/holidays")
    public R<List<HolidayVo>> holidays(@RequestParam(required = false) Integer year) {
        return R.ok(calendarService.selectHolidays(year));
    }

    @SaCheckPermission("at:calendar:edit")
    @Log(title = "节假日登记", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/holidays")
    public R<HolidayVo> addHoliday(@Validated @RequestBody HolidayBo bo) {
        return R.ok(calendarService.addHoliday(bo));
    }

    @SaCheckPermission("at:calendar:edit")
    @Log(title = "节假日登记", businessType = BusinessType.DELETE)
    @DeleteMapping("/holidays/{holidayId}")
    public R<Void> removeHoliday(@PathVariable Long holidayId) {
        calendarService.deleteHoliday(holidayId);
        return R.ok();
    }
}

package org.dromara.agentoa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.OaCalendar;
import org.dromara.agentoa.attendance.domain.OaHoliday;
import org.dromara.agentoa.attendance.domain.bo.CalendarBo;
import org.dromara.agentoa.attendance.domain.bo.HolidayBo;
import org.dromara.agentoa.attendance.domain.vo.CalendarVo;
import org.dromara.agentoa.attendance.domain.vo.HolidayVo;
import org.dromara.agentoa.attendance.mapper.OaCalendarMapper;
import org.dromara.agentoa.attendance.mapper.OaHolidayMapper;
import org.dromara.agentoa.attendance.service.IAttendanceCalendarService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 工作日历实现：日期类型优先于班次工作日；节假日登记自动生成同名日历行。
 */
@Service
@RequiredArgsConstructor
public class AttendanceCalendarServiceImpl implements IAttendanceCalendarService {

    private final OaCalendarMapper calendarMapper;
    private final OaHolidayMapper holidayMapper;

    @Override
    public List<CalendarVo> selectCalendar(Integer year, Integer month) {
        if (year == null) {
            throw new ServiceException("年度不能为空", 400);
        }
        LocalDate from = LocalDate.of(year, month == null ? 1 : month, 1);
        LocalDate to = month == null ? from.plusYears(1).minusDays(1) : from.plusMonths(1).minusDays(1);
        List<CalendarVo> records = MapstructUtils.convert(calendarMapper.selectList(new LambdaQueryWrapper<OaCalendar>()
            .ge(OaCalendar::getWorkDate, from)
            .le(OaCalendar::getWorkDate, to)
            .orderByAsc(OaCalendar::getWorkDate)), CalendarVo.class);
        return records == null ? List.of() : records;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CalendarVo> saveCalendar(List<CalendarBo> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new ServiceException("日历行不能为空", 400);
        }
        for (CalendarBo row : rows) {
            if (row.getWorkDate() == null || row.getDayType() == null) {
                throw new ServiceException("日期与类型不能为空", 400);
            }
            if (!List.of("0", "1", "2").contains(row.getDayType())) {
                throw new ServiceException("日期类型取值非法", 400);
            }
            OaCalendar existing = calendarMapper.selectOne(new LambdaQueryWrapper<OaCalendar>()
                .eq(OaCalendar::getWorkDate, row.getWorkDate())
                .last("LIMIT 1"));
            if (existing == null) {
                OaCalendar calendar = new OaCalendar();
                calendar.setWorkDate(row.getWorkDate());
                calendar.setDayType(row.getDayType());
                calendar.setDescription(row.getDescription());
                calendar.setRuleVersion(1);
                calendar.setCreateBy(LoginHelper.getUserId());
                calendar.setCreateTime(new Date());
                calendarMapper.insert(calendar);
            } else {
                existing.setDayType(row.getDayType());
                existing.setDescription(row.getDescription());
                existing.setRuleVersion(existing.getRuleVersion() == null ? 1 : existing.getRuleVersion() + 1);
                existing.setUpdateBy(LoginHelper.getUserId());
                existing.setUpdateTime(new Date());
                calendarMapper.updateById(existing);
            }
        }
        Integer year = rows.get(0).getWorkDate().getYear();
        return selectCalendar(year, null).stream()
            .filter(vo -> rows.stream().anyMatch(row -> row.getWorkDate().equals(vo.getWorkDate())))
            .toList();
    }

    @Override
    public List<HolidayVo> selectHolidays(Integer year) {
        List<HolidayVo> records = MapstructUtils.convert(holidayMapper.selectList(
            new LambdaQueryWrapper<OaHoliday>()
                .eq(year != null, OaHoliday::getYear, year)
                .orderByAsc(OaHoliday::getHolidayDate)), HolidayVo.class);
        return records == null ? List.of() : records;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HolidayVo addHoliday(HolidayBo bo) {
        if (!"HOLIDAY".equals(bo.getHolidayType()) && !"MAKEUP".equals(bo.getHolidayType())) {
            throw new ServiceException("节假日类型取值非法", 400);
        }
        OaHoliday holiday = new OaHoliday();
        holiday.setHolidayDate(bo.getHolidayDate());
        holiday.setHolidayName(bo.getHolidayName());
        holiday.setHolidayType(bo.getHolidayType());
        holiday.setYear(bo.getHolidayDate().getYear());
        holiday.setCreateBy(LoginHelper.getUserId());
        holiday.setCreateTime(new Date());
        try {
            holidayMapper.insert(holiday);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("该日期已登记节假日", 409);
        }
        syncCalendar(bo);
        return MapstructUtils.convert(holiday, HolidayVo.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteHoliday(Long id) {
        OaHoliday holiday = holidayMapper.selectById(id);
        if (holiday == null) {
            throw new ServiceException("节假日不存在", 404);
        }
        holidayMapper.deleteById(id);
    }

    /** 节假日登记同步写入工作日历，保证判定口径一致 */
    private void syncCalendar(HolidayBo bo) {
        OaCalendar existing = calendarMapper.selectOne(new LambdaQueryWrapper<OaCalendar>()
            .eq(OaCalendar::getWorkDate, bo.getHolidayDate())
            .last("LIMIT 1"));
        String dayType = "MAKEUP".equals(bo.getHolidayType()) ? "2" : "1";
        if (existing == null) {
            OaCalendar calendar = new OaCalendar();
            calendar.setWorkDate(bo.getHolidayDate());
            calendar.setDayType(dayType);
            calendar.setDescription(bo.getHolidayName());
            calendar.setRuleVersion(1);
            calendar.setCreateBy(LoginHelper.getUserId());
            calendar.setCreateTime(new Date());
            calendarMapper.insert(calendar);
        } else {
            existing.setDayType(dayType);
            existing.setDescription(bo.getHolidayName());
            existing.setRuleVersion(existing.getRuleVersion() == null ? 1 : existing.getRuleVersion() + 1);
            existing.setUpdateBy(LoginHelper.getUserId());
            existing.setUpdateTime(new Date());
            calendarMapper.updateById(existing);
        }
    }
}

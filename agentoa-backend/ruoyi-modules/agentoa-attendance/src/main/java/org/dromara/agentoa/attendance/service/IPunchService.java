package org.dromara.agentoa.attendance.service;

import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.bo.PunchBo;
import org.dromara.agentoa.attendance.domain.vo.PunchRecordVo;
import org.dromara.agentoa.attendance.domain.vo.PunchTodayVo;
import org.dromara.agentoa.attendance.domain.vo.PunchVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;

import java.time.LocalDate;

/**
 * 打卡服务（API 规范 5.1）：服务器接收时间为事实，原始记录全部保留，迟到早退是计算结果。
 */
public interface IPunchService {

    /** 打卡；idempotencyKey 为空时不启用重放，仅按原始记录保留 */
    PunchVo punch(PunchBo bo, String idempotencyKey);

    PunchTodayVo today();

    PageVo<PunchRecordVo> selectRecords(Long userId, LocalDate dateFrom, LocalDate dateTo, AttendancePageQuery page);
}

package org.dromara.agentoa.attendance.controller;

import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.bo.PunchBo;
import org.dromara.agentoa.attendance.domain.vo.PunchRecordVo;
import org.dromara.agentoa.attendance.domain.vo.PunchTodayVo;
import org.dromara.agentoa.attendance.domain.vo.PunchVo;
import org.dromara.agentoa.attendance.service.IPunchService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 打卡接口（API 规范 5.1）：Idempotency-Key 必填，同键重放返回首次结果。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/attendance/punch")
public class AttendancePunchController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final IPunchService punchService;

    @Log(title = "考勤打卡", businessType = BusinessType.INSERT)
    @PostMapping
    public R<PunchVo> punch(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                            @Validated @RequestBody PunchBo bo) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ServiceException("缺少 Idempotency-Key 请求头", 400);
        }
        return R.ok(punchService.punch(bo, idempotencyKey));
    }

    @GetMapping("/today")
    public R<PunchTodayVo> today() {
        return R.ok(punchService.today());
    }

    @GetMapping("/records")
    public R<PageVo<PunchRecordVo>> records(@RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) LocalDate dateFrom,
                                            @RequestParam(required = false) LocalDate dateTo,
                                            AttendancePageQuery page) {
        Long scopeUserId = userId == null ? punchScopeSelf() : userId;
        return R.ok(punchService.selectRecords(scopeUserId, dateFrom, dateTo, page));
    }

    /** 无查询他人权限时固定本人范围 */
    private Long punchScopeSelf() {
        return org.dromara.common.satoken.utils.LoginHelper.getUserId();
    }
}

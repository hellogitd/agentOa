package org.dromara.agentoa.collaboration.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.collaboration.domain.bo.BookingBo;
import org.dromara.agentoa.collaboration.domain.bo.CollabPageQuery;
import org.dromara.agentoa.collaboration.domain.vo.BookingVo;
import org.dromara.agentoa.collaboration.service.IRoomBookingService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会议室预约接口（API 规范 9.2）：预约必须携带 Idempotency-Key；签到/取消为对象级授权。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/calendar")
public class RoomBookingController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final IRoomBookingService bookingService;

    @GetMapping("/rooms/{id}/bookings")
    public R<PageVo<BookingVo>> list(@PathVariable Long id,
                                     @RequestParam(required = false) String start,
                                     @RequestParam(required = false) String end,
                                     CollabPageQuery query) {
        return R.ok(bookingService.list(id, start, end, query));
    }

    @SaCheckPermission("cl:room:book")
    @Log(title = "会议室预约", businessType = BusinessType.INSERT)
    @PostMapping("/rooms/{id}/bookings")
    public R<BookingVo> book(@PathVariable Long id,
                             @RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                             @Validated @RequestBody BookingBo bo) {
        return R.ok(bookingService.book(id, bo, idempotencyKey, LoginHelper.getUserId()));
    }

    @GetMapping("/bookings/{id}")
    public R<BookingVo> detail(@PathVariable Long id) {
        return R.ok(bookingService.detail(id, LoginHelper.getUserId()));
    }

    @Log(title = "会议室签到", businessType = BusinessType.UPDATE)
    @PutMapping("/bookings/{id}/checkin")
    public R<BookingVo> checkin(@PathVariable Long id) {
        return R.ok(bookingService.checkin(id, LoginHelper.getUserId()));
    }

    @Log(title = "会议室取消", businessType = BusinessType.UPDATE)
    @PutMapping("/bookings/{id}/cancel")
    public R<BookingVo> cancel(@PathVariable Long id) {
        return R.ok(bookingService.cancel(id, LoginHelper.getUserId()));
    }
}

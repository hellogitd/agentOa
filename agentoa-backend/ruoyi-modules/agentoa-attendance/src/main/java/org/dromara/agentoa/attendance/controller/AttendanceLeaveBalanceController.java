package org.dromara.agentoa.attendance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.attendance.domain.bo.BalanceGrantBo;
import org.dromara.agentoa.attendance.domain.bo.AttendancePageQuery;
import org.dromara.agentoa.attendance.domain.policy.AttendanceAccessPolicy;
import org.dromara.agentoa.attendance.domain.vo.BalanceVo;
import org.dromara.agentoa.attendance.domain.vo.BatchVo;
import org.dromara.agentoa.attendance.domain.vo.LedgerVo;
import org.dromara.agentoa.attendance.service.ILeaveBalanceService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 假期额度接口（API 规范 5.3 / docs/13）：本人余额自助查询，按人查询与发放需 HR 授权。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/attendance/leaves")
public class AttendanceLeaveBalanceController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final ILeaveBalanceService balanceService;

    @GetMapping("/balance")
    public R<List<BalanceVo>> myBalance(@RequestParam(required = false) Integer year) {
        return R.ok(balanceService.selectBalances(LoginHelper.getUserId(), year));
    }

    @SaCheckPermission("at:leave:query")
    @GetMapping("/balance/{userId}")
    public R<List<BalanceVo>> userBalance(@PathVariable Long userId,
                                          @RequestParam(required = false) Integer year) {
        return R.ok(balanceService.selectBalances(userId, year));
    }

    @SaCheckPermission("at:leave:grant")
    @Log(title = "假期额度发放", businessType = BusinessType.INSERT)
    @PostMapping("/balance/grant")
    public R<BalanceVo> grant(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                              @Validated @RequestBody BalanceGrantBo bo) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ServiceException("缺少 Idempotency-Key 请求头", 400);
        }
        return R.ok(balanceService.grant(bo, idempotencyKey, LoginHelper.getUserId()));
    }

    @GetMapping("/ledger")
    public R<PageVo<LedgerVo>> ledger(@RequestParam(required = false) Long userId,
                                      @RequestParam(required = false) Integer year,
                                      @RequestParam(required = false) String leaveType,
                                      AttendancePageQuery page) {
        Long scopeUserId = userId;
        if (scopeUserId != null && !scopeUserId.equals(LoginHelper.getUserId()) && !canViewOthers()) {
            throw new ServiceException("WF_FORBIDDEN 无权查询他人额度账本", 403);
        }
        return R.ok(balanceService.selectLedger(scopeUserId, year, leaveType, page));
    }

    /** 额度批次（AT-06）：本人自助，查询他人需 at:leave:query */
    @GetMapping("/batches")
    public R<List<BatchVo>> batches(@RequestParam(required = false) Long userId,
                                    @RequestParam(required = false) Integer year,
                                    @RequestParam(required = false) String leaveType) {
        Long scopeUserId = userId == null ? LoginHelper.getUserId() : userId;
        if (!scopeUserId.equals(LoginHelper.getUserId()) && !canViewOthers()) {
            throw new ServiceException("WF_FORBIDDEN 无权查询他人额度批次", 403);
        }
        return R.ok(balanceService.selectBatches(scopeUserId, year, leaveType));
    }

    /** 批次过期扫描（幂等，可由调度触发；对齐 timeout-scan 先例） */
    @SaCheckPermission("at:leave:grant")
    @Log(title = "假期批次过期", businessType = BusinessType.UPDATE)
    @PostMapping("/expire-scan")
    public R<Integer> expireScan() {
        return R.ok(balanceService.expireBatches(LoginHelper.getUserId()));
    }

    private boolean canViewOthers() {
        Set<String> permissions = LoginHelper.getLoginUser() == null ? Set.of() : LoginHelper.getLoginUser().getMenuPermission();
        return AttendanceAccessPolicy.canViewOthers(LoginHelper.isSuperAdmin(), permissions);
    }
}

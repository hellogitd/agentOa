package org.dromara.agentoa.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.finance.domain.bo.FinancePageQuery;
import org.dromara.agentoa.finance.domain.bo.PaymentBo;
import org.dromara.agentoa.finance.domain.policy.FinanceAccessPolicy;
import org.dromara.agentoa.finance.domain.vo.PendingClaimVo;
import org.dromara.agentoa.finance.domain.vo.PaymentVo;
import org.dromara.agentoa.finance.service.IPaymentService;
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

import java.util.Set;

/**
 * 报销单的财务侧入口（API 规范 6.1/6.4）：付款唯一写入口与待付款清单。
 * 报销草稿 CRUD 与提交由模块 2 承接单接口提供（同一路径前缀，不同资源）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/finance/reimburses")
public class FinanceReimburseController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final IPaymentService paymentService;

    /** 登记人工全额付款（docs/05 6.4）：出纳/财务角色 + 幂等键 + lockVersion */
    @SaCheckPermission("fn:payment:add")
    @Log(title = "报销付款登记", businessType = BusinessType.INSERT)
    @PostMapping("/{reimburseId}/pay")
    public R<PaymentVo> pay(@RequestHeader(IDEMPOTENCY_HEADER) @Size(max = 64) String idempotencyKey,
                            @PathVariable Long reimburseId,
                            @Validated @RequestBody PaymentBo bo) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ServiceException("缺少 Idempotency-Key 请求头", 400);
        }
        if (!FinanceAccessPolicy.canRegisterPayment(roleKeys())) {
            throw new ServiceException("WF_FORBIDDEN 付款登记需财务或出纳角色", 403);
        }
        return R.ok(paymentService.pay(reimburseId, bo, idempotencyKey, LoginHelper.getUserId()));
    }

    /** 待付款/已通过报销单（财务/出纳视角，docs/14 第 5 步） */
    @SaCheckPermission("fn:payment:list")
    @GetMapping("/pending")
    public R<PageVo<PendingClaimVo>> pending(@RequestParam(required = false) Integer status,
                                             FinancePageQuery page) {
        return R.ok(paymentService.pendingClaims(status, page));
    }

    private Set<String> roleKeys() {
        var loginUser = LoginHelper.getLoginUser();
        return loginUser == null || loginUser.getRolePermission() == null ? Set.of() : loginUser.getRolePermission();
    }
}

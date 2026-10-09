package org.dromara.agentoa.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.finance.domain.bo.FinancePageQuery;
import org.dromara.agentoa.finance.domain.vo.PaymentVo;
import org.dromara.agentoa.finance.service.IPaymentService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 付款记录查询（API 规范 6.4）：付款唯一写入口是 POST /finance/reimburses/{id}/pay。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/finance/payments")
public class FinancePaymentController {

    private final IPaymentService paymentService;

    @SaCheckPermission("fn:payment:list")
    @GetMapping
    public R<PageVo<PaymentVo>> list(FinancePageQuery page) {
        return R.ok(paymentService.list(page));
    }

    @SaCheckPermission("fn:payment:list")
    @GetMapping("/{id}")
    public R<PaymentVo> get(@PathVariable Long id) {
        return R.ok(paymentService.get(id));
    }
}

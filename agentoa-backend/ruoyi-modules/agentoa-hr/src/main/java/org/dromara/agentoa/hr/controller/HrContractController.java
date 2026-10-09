package org.dromara.agentoa.hr.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.hr.domain.bo.HrPageQuery;
import org.dromara.agentoa.hr.domain.bo.OaContractBo;
import org.dromara.agentoa.hr.domain.vo.OaContractVo;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.agentoa.hr.service.IHrContractService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 合同管理接口（需求 HR-09）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/hr/contracts")
public class HrContractController {

    private final IHrContractService contractService;

    @SaCheckPermission("hr:contract:list")
    @GetMapping
    public R<PageVo<OaContractVo>> list(OaContractBo query, HrPageQuery page) {
        return R.ok(contractService.selectPageContracts(query, page));
    }

    @SaCheckPermission("hr:contract:query")
    @GetMapping("/{contractId}")
    public R<OaContractVo> get(@PathVariable Long contractId) {
        return R.ok(contractService.selectContract(contractId));
    }

    /** 续签提醒：days 天内到期的生效合同 */
    @SaCheckPermission("hr:contract:query")
    @GetMapping("/expiring")
    public R<List<OaContractVo>> expiring(@RequestParam(defaultValue = "30") int days) {
        return R.ok(contractService.selectExpiring(days));
    }

    @SaCheckPermission("hr:contract:add")
    @Log(title = "合同管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<OaContractVo> add(@Validated @RequestBody OaContractBo bo) {
        return R.ok(contractService.createContract(bo));
    }

    @SaCheckPermission("hr:contract:edit")
    @Log(title = "合同管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/{contractId}")
    public R<OaContractVo> edit(@PathVariable Long contractId, @Validated @RequestBody OaContractBo bo) {
        return R.ok(contractService.updateContract(contractId, bo));
    }

    @SaCheckPermission("hr:contract:remove")
    @Log(title = "合同管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{contractId}")
    public R<Void> remove(@PathVariable Long contractId) {
        contractService.deleteContract(contractId);
        return R.ok();
    }
}

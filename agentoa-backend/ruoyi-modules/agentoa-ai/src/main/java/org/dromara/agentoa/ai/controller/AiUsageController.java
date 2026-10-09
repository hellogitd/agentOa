package org.dromara.agentoa.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiUsageQueryBo;
import org.dromara.agentoa.ai.domain.policy.AiAccessPolicy;
import org.dromara.agentoa.ai.domain.vo.AiUsageLogVo;
import org.dromara.agentoa.ai.domain.vo.AiUsageStatVo;
import org.dromara.agentoa.ai.service.IAiUsageService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用量与配额统计（docs/21 AI-M1-09）。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai/usage")
public class AiUsageController {

    private final IAiUsageService usageService;

    @SaCheckPermission(AiAccessPolicy.PERM_USAGE_LIST)
    @GetMapping("/stats")
    public R<AiUsageStatVo> stats(AiUsageQueryBo query) {
        return R.ok(usageService.stats(query));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_USAGE_LIST)
    @GetMapping("/logs")
    public R<PageVo<AiUsageLogVo>> logs(AiUsageQueryBo query, AiPageQuery page) {
        return R.ok(usageService.logs(query, page));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_USAGE_EXPORT)
    @Log(title = "AI 用量导出", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public void export(AiUsageQueryBo query, HttpServletResponse response) {
        usageService.export(query, response);
    }
}

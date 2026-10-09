package org.dromara.agentoa.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.ai.domain.bo.AiPageQuery;
import org.dromara.agentoa.ai.domain.bo.AiToolBo;
import org.dromara.agentoa.ai.domain.policy.AiAccessPolicy;
import org.dromara.agentoa.ai.domain.vo.AiToolDiscoveryVo;
import org.dromara.agentoa.ai.domain.vo.AiToolTestVo;
import org.dromara.agentoa.ai.domain.vo.AiToolVo;
import org.dromara.agentoa.ai.service.IAiToolService;
import org.dromara.agentoa.hr.domain.vo.PageVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工具与 MCP 管理（docs/21 M5）。
 * <p>
 * MCP 鉴权头只写不读，回显仅给连接地址与是否已配置鉴权头（docs/21 §2.3）。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai/tools")
public class AiToolController {

    private final IAiToolService toolService;

    @SaCheckPermission(AiAccessPolicy.PERM_TOOL_QUERY)
    @GetMapping
    public R<PageVo<AiToolVo>> list(AiPageQuery page) {
        return R.ok(toolService.page(page));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_TOOL_QUERY)
    @GetMapping("/{id}")
    public R<AiToolVo> get(@PathVariable Long id) {
        return R.ok(toolService.get(id));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_TOOL_ADD)
    @Log(title = "AI 工具", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @RepeatSubmit()
    @PostMapping
    public R<AiToolVo> add(@Validated @RequestBody AiToolBo bo) {
        return R.ok(toolService.create(bo));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_TOOL_EDIT)
    @Log(title = "AI 工具", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @RepeatSubmit()
    @PutMapping("/{id}")
    public R<AiToolVo> edit(@PathVariable Long id, @Validated @RequestBody AiToolBo bo) {
        bo.setId(id);
        return R.ok(toolService.update(bo));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_TOOL_REMOVE)
    @Log(title = "AI 工具", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        toolService.delete(id);
        return R.ok();
    }

    @SaCheckPermission(AiAccessPolicy.PERM_TOOL_TEST)
    @Log(title = "AI 工具", businessType = BusinessType.OTHER, isSaveRequestData = false)
    @RepeatSubmit()
    @PostMapping("/{id}/test")
    public R<AiToolTestVo> test(@PathVariable Long id) {
        return R.ok(toolService.test(id));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_TOOL_QUERY)
    @GetMapping("/discover/{mcpServerId}")
    public R<List<AiToolDiscoveryVo>> discover(@PathVariable Long mcpServerId) {
        return R.ok(toolService.discover(mcpServerId));
    }

    @SaCheckPermission(AiAccessPolicy.PERM_TOOL_ADD)
    @Log(title = "AI 工具", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @RepeatSubmit()
    @PostMapping("/discover/{mcpServerId}/import")
    public R<List<AiToolVo>> importDiscovered(@PathVariable Long mcpServerId) {
        return R.ok(toolService.importDiscovered(mcpServerId));
    }
}

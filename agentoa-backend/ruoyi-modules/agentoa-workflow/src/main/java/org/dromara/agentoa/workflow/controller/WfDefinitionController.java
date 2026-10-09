package org.dromara.agentoa.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.bo.DefinitionBo;
import org.dromara.agentoa.workflow.domain.bo.DefinitionStatusBo;
import org.dromara.agentoa.workflow.domain.vo.DefinitionVo;
import org.dromara.agentoa.workflow.domain.vo.FormVo;
import org.dromara.agentoa.workflow.domain.vo.LaunchCatalogVo;
import org.dromara.agentoa.workflow.domain.vo.LaunchDefinitionVo;
import org.dromara.agentoa.workflow.service.IWorkflowTemplateService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 流程定义管理（docs/05 section 4.1/4.2）。
 * <p>
 * 只开放结构化审批链配置（表单 + 审批链），不开放 BPMN 上传：后端由 {@code FlowChainCompiler}
 * 编译为受控 BPMN 并复用 {@code BpmnTemplateValidator} 做编译门禁。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/wf")
public class WfDefinitionController {

    private final IWorkflowTemplateService templateService;

    @SaCheckPermission("wf:definition:list")
    @GetMapping("/definitions")
    public R<List<DefinitionVo>> list() {
        return R.ok(templateService.selectDefinitions());
    }

    @SaCheckPermission("wf:definition:query")
    @GetMapping("/definitions/{definitionId}")
    public R<DefinitionVo> get(@PathVariable Long definitionId) {
        return R.ok(templateService.selectDefinition(definitionId));
    }

    /**
     * 发起申请目录（H5/移动端）：登录即可读，不校验 wf:definition:list。
     * 只返回启用分类 + 已发布流程定义（含已发布表单快照），与管理端
     * {@code GET /definitions}（全量含草稿/停用）区分，保证后台启停/发布/分类调整即时同步到发起端。
     */
    @GetMapping("/launchable")
    public R<LaunchCatalogVo> launchable() {
        return R.ok(templateService.selectLaunchCatalog());
    }

    /** 单个可发起流程（H5 发起页按 definitionId 取元数据 + 表单快照），登录即可读。 */
    @GetMapping("/launchable/{definitionId}")
    public R<LaunchDefinitionVo> launchableDetail(@PathVariable Long definitionId) {
        return R.ok(templateService.selectLaunchableDefinition(definitionId));
    }

    @SaCheckPermission("wf:definition:add")
    @Log(title = "流程定义", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping("/definitions")
    public R<DefinitionVo> create(@Validated @RequestBody DefinitionBo bo) {
        return R.ok(templateService.createDefinition(bo));
    }

    @SaCheckPermission("wf:definition:edit")
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/definitions/{definitionId}")
    public R<DefinitionVo> edit(@PathVariable Long definitionId, @Validated @RequestBody DefinitionBo bo) {
        return R.ok(templateService.updateDefinition(definitionId, bo));
    }

    @SaCheckPermission("wf:definition:edit")
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/definitions/{definitionId}/status")
    public R<DefinitionVo> changeStatus(@PathVariable Long definitionId,
                                        @Validated @RequestBody DefinitionStatusBo bo) {
        return R.ok(templateService.changeStatus(definitionId, bo));
    }

    @SaCheckPermission("wf:definition:remove")
    @Log(title = "流程定义", businessType = BusinessType.DELETE)
    @RepeatSubmit()
    @DeleteMapping("/definitions/{definitionId}")
    public R<Void> remove(@PathVariable Long definitionId) {
        templateService.deleteDefinition(definitionId);
        return R.ok();
    }

    /** 一键启用全部内置模板 */
    @SaCheckPermission("wf:definition:enable")
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/definitions/builtin/enable-all")
    public R<Integer> enableBuiltin() {
        return R.ok(templateService.enableBuiltinTemplates());
    }

    @SaCheckPermission("wf:definition:add")
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    @PostMapping("/definitions/{definitionId}/publish")
    public R<DefinitionVo> publish(@PathVariable Long definitionId,
                                   @RequestParam(required = false) Integer versionNo) {
        return R.ok(templateService.publish(definitionId, versionNo));
    }

    @SaCheckPermission("wf:definition:query")
    @GetMapping("/definitions/{definitionId}/xml")
    public R<String> xml(@PathVariable Long definitionId, @RequestParam(required = false) Integer versionNo) {
        return R.ok(templateService.selectBpmnXml(definitionId, versionNo));
    }

    @SaCheckPermission("wf:definition:query")
    @GetMapping("/definitions/{definitionId}/diagram")
    public void diagram(@PathVariable Long definitionId, @RequestParam(required = false) Integer versionNo,
                        HttpServletResponse response) throws IOException {
        response.setContentType("image/png");
        response.getOutputStream().write(templateService.definitionDiagram(definitionId, versionNo));
    }

    @SaCheckPermission("wf:form:list")
    @GetMapping("/forms")
    public R<List<FormVo>> forms() {
        return R.ok(templateService.selectForms());
    }

    @SaCheckPermission("wf:form:query")
    @GetMapping("/forms/{formKey}")
    public R<FormVo> form(@PathVariable String formKey) {
        return R.ok(templateService.selectForm(formKey));
    }
}

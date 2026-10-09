package org.dromara.agentoa.workflow.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.agentoa.workflow.domain.OaFlowDefinition;
import org.dromara.agentoa.workflow.domain.OaFlowDefinitionVersion;
import org.dromara.agentoa.workflow.domain.OaFlowFormVersion;
import org.dromara.agentoa.workflow.domain.enums.FlowTemplateStatus;
import org.dromara.agentoa.workflow.domain.template.BuiltInTemplateSpec;
import org.dromara.agentoa.workflow.mapper.OaFlowDefinitionMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowDefinitionVersionMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowFormVersionMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 内置模板注册机制（docs/12）：26 个内置模板随代码仓库交付，注册时把「表单设计 + 推荐审批链」
 * 经 {@link FlowChainCompiler} 编译为受控 BPMN 并部署到 Flowable，记录校验摘要，版本发布后不可变。
 * <p>
 * 注册与用户在向导里创建自定义流程走完全相同的编译与校验路径，不存在「内置模板后门」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateRegistrar {

    private final OaFlowDefinitionMapper definitionMapper;
    private final OaFlowDefinitionVersionMapper versionMapper;
    private final OaFlowFormVersionMapper formVersionMapper;
    private final RepositoryService repositoryService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        registerAll();
    }

    /** 是否为内置模板（内置模板禁止删除，只能停用） */
    public static boolean isBuiltin(String processKey) {
        return BuiltInTemplateCatalog.isBuiltin(processKey);
    }

    /** 取内置模板规格，非内置返回 null */
    public static BuiltInTemplateSpec specOf(String processKey) {
        return BuiltInTemplateCatalog.of(processKey);
    }

    public void registerAll() {
        List<BuiltInTemplateSpec> specs = BuiltInTemplateCatalog.all();
        for (BuiltInTemplateSpec spec : specs) {
            registerOne(spec);
        }
        log.info("内置流程模板注册完成: {} 个", specs.size());
    }

    public void registerOne(BuiltInTemplateSpec spec) {
        OaFlowDefinition definition = definitionMapper.selectOne(new LambdaQueryWrapper<OaFlowDefinition>()
            .eq(OaFlowDefinition::getProcessKey, spec.getProcessKey()));
        if (definition == null) {
            definition = new OaFlowDefinition();
            definition.setProcessKey(spec.getProcessKey());
            definition.setProcessName(spec.getProcessName());
            definition.setCategoryId(spec.getCategoryId());
            definition.setFormKey(spec.getFormKey());
            definition.setBusinessType(spec.getBusinessType());
            definition.setCurrentVersionNo(1);
            definition.setStatus(FlowTemplateStatus.DRAFT.name());
            definition.setIcon(spec.getIcon());
            definition.setSort(spec.getSort() == null ? 0 : spec.getSort());
            definition.setRemark(spec.getRemark());
            definitionMapper.insert(definition);
        }

        String schemaJson = FormSchemaSupport.validateAndSerialize(spec.getForm());
        upsertFormVersion(spec.getFormKey(), spec.getFormName(), schemaJson);

        String chainJson = PlainJson.toJson(spec.getChain());
        FlowChainCompiler.CompiledFlow compiled =
            FlowChainCompiler.compile(spec.getChain(), spec.getProcessKey(), spec.getProcessName());
        String bpmnDigest = FlowIdempotencyGuard.digest(compiled.bpmnXml());

        OaFlowDefinitionVersion version = versionMapper.selectOne(new LambdaQueryWrapper<OaFlowDefinitionVersion>()
            .eq(OaFlowDefinitionVersion::getDefinitionId, definition.getId())
            .eq(OaFlowDefinitionVersion::getBpmnDigest, bpmnDigest));
        if (version == null) {
            Integer maxVersion = versionMapper.selectList(new LambdaQueryWrapper<OaFlowDefinitionVersion>()
                    .eq(OaFlowDefinitionVersion::getDefinitionId, definition.getId()))
                .stream().map(OaFlowDefinitionVersion::getVersionNo).max(Integer::compareTo).orElse(0);
            version = new OaFlowDefinitionVersion();
            version.setDefinitionId(definition.getId());
            version.setVersionNo(maxVersion + 1);
            version.setBpmnResource("chain://" + spec.getProcessKey() + "/v" + (maxVersion + 1));
            version.setBpmnXml(compiled.bpmnXml());
            version.setChainJson(chainJson);
            version.setBpmnDigest(bpmnDigest);
            version.setValidationSummary(compiled.validationSummary());
            version.setStatus(FlowTemplateStatus.DRAFT.name());
            version.setCreateTime(new Date());
            versionMapper.insert(version);
        }
        if (!FlowTemplateStatus.PUBLISHED.name().equals(version.getStatus()) || version.getFlowableProcDefId() == null) {
            deployVersion(definition, version);
        }
    }

    private void deployVersion(OaFlowDefinition definition, OaFlowDefinitionVersion version) {
        String bpmnXml = version.getBpmnXml();
        var deployment = repositoryService.createDeployment()
            .name("agentoa-" + definition.getProcessKey() + "-v" + version.getVersionNo())
            .key("agentoa-" + definition.getProcessKey())
            .addString(definition.getProcessKey() + ".bpmn20.xml", bpmnXml)
            .deploy();
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .deploymentId(deployment.getId()).singleResult();
        version.setFlowableDeploymentId(deployment.getId());
        version.setFlowableProcDefId(processDefinition.getId());
        version.setStatus(FlowTemplateStatus.PUBLISHED.name());
        if (version.getPublishedTime() == null) {
            version.setPublishedTime(new Date());
        }
        versionMapper.updateById(version);

        definition.setStatus(FlowTemplateStatus.PUBLISHED.name());
        definition.setCurrentVersionNo(version.getVersionNo());
        definitionMapper.updateById(definition);
    }

    private void upsertFormVersion(String formKey, String formName, String schemaJson) {
        String digest = FlowIdempotencyGuard.digest(schemaJson);
        OaFlowFormVersion existing = formVersionMapper.selectOne(new LambdaQueryWrapper<OaFlowFormVersion>()
            .eq(OaFlowFormVersion::getFormKey, formKey)
            .eq(OaFlowFormVersion::getSchemaDigest, digest));
        if (existing != null) {
            return;
        }
        Integer maxVersion = formVersionMapper.selectList(new LambdaQueryWrapper<OaFlowFormVersion>()
                .eq(OaFlowFormVersion::getFormKey, formKey))
            .stream().map(OaFlowFormVersion::getVersionNo).max(Integer::compareTo).orElse(0);
        OaFlowFormVersion formVersion = new OaFlowFormVersion();
        formVersion.setFormKey(formKey);
        formVersion.setFormName(formName);
        formVersion.setVersionNo(maxVersion + 1);
        formVersion.setSchemaJson(schemaJson);
        formVersion.setSchemaDigest(digest);
        formVersion.setStatus(FlowTemplateStatus.PUBLISHED.name());
        formVersion.setCreateTime(new Date());
        formVersionMapper.insert(formVersion);
    }
}

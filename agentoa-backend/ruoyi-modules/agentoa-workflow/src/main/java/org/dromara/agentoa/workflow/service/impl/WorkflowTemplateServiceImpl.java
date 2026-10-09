package org.dromara.agentoa.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.domain.OaFlowCategory;
import org.dromara.agentoa.workflow.domain.OaFlowDefinition;
import org.dromara.agentoa.workflow.domain.OaFlowDefinitionVersion;
import org.dromara.agentoa.workflow.domain.OaFlowFormVersion;
import org.dromara.agentoa.workflow.domain.OaFlowInstance;
import org.dromara.agentoa.workflow.domain.bo.CategoryBo;
import org.dromara.agentoa.workflow.domain.bo.DefinitionBo;
import org.dromara.agentoa.workflow.domain.bo.DefinitionStatusBo;
import org.dromara.agentoa.workflow.domain.bo.FormDesignBo;
import org.dromara.agentoa.workflow.domain.bo.FormSchemaBo;
import org.dromara.agentoa.workflow.domain.chain.FlowChainConfig;
import org.dromara.agentoa.workflow.domain.enums.FlowTemplateStatus;
import org.dromara.agentoa.workflow.domain.vo.CategoryVo;
import org.dromara.agentoa.workflow.domain.vo.DefinitionVo;
import org.dromara.agentoa.workflow.domain.vo.FormVo;
import org.dromara.agentoa.workflow.domain.vo.LaunchCatalogVo;
import org.dromara.agentoa.workflow.domain.vo.LaunchDefinitionVo;
import org.dromara.agentoa.workflow.domain.vo.SelectableNodeVo;
import org.dromara.agentoa.workflow.font.DiagramFontProvider;
import org.dromara.agentoa.workflow.mapper.OaFlowCategoryMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowDefinitionMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowDefinitionVersionMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowFormVersionMapper;
import org.dromara.agentoa.workflow.mapper.OaFlowInstanceMapper;
import org.dromara.agentoa.workflow.mapper.WorkflowIdentityReadMapper;
import org.dromara.agentoa.workflow.service.IWorkflowTemplateService;
import org.dromara.agentoa.workflow.service.support.BpmnSource;
import org.dromara.agentoa.workflow.service.support.BpmnTemplateValidator;
import org.dromara.agentoa.workflow.service.support.BuiltInTemplateCatalog;
import org.dromara.agentoa.workflow.service.support.FlowChainCompiler;
import org.dromara.agentoa.workflow.service.support.FlowIdempotencyGuard;
import org.dromara.agentoa.workflow.service.support.FormSchemaSupport;
import org.dromara.agentoa.workflow.service.support.TemplateRegistrar;
import org.dromara.agentoa.workflow.service.support.UserSelectResolver;
import org.dromara.agentoa.workflow.service.support.WorkflowOrchestrator;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.agentoa.workflow.service.support.PlainJson;
import org.dromara.common.satoken.utils.LoginHelper;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.image.impl.DefaultProcessDiagramGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WorkflowTemplateServiceImpl implements IWorkflowTemplateService {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final Pattern BUSINESS_TYPE_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,31}");
    /** oa_flow_category.status：0 正常 / 1 停用 */
    private static final String CATEGORY_ACTIVE = "0";

    private final OaFlowCategoryMapper categoryMapper;
    private final OaFlowDefinitionMapper definitionMapper;
    private final OaFlowDefinitionVersionMapper versionMapper;
    private final OaFlowFormVersionMapper formVersionMapper;
    private final OaFlowInstanceMapper instanceMapper;
    private final RepositoryService repositoryService;
    private final WorkflowIdentityReadMapper identityMapper;

    // ------------------------------------------------------------------ 分类

    @Override
    public List<CategoryVo> selectCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<OaFlowCategory>()
                .orderByAsc(OaFlowCategory::getSort))
            .stream().map(this::toCategoryVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryVo createCategory(CategoryBo bo) {
        Long exists = categoryMapper.selectCount(new LambdaQueryWrapper<OaFlowCategory>()
            .eq(OaFlowCategory::getCode, bo.getCode()));
        if (exists != null && exists > 0) {
            throw new ServiceException("分类编码已存在", 409);
        }
        OaFlowCategory category = new OaFlowCategory();
        category.setCode(bo.getCode());
        category.setName(bo.getName());
        category.setSort(bo.getSort() == null ? 0 : bo.getSort());
        category.setStatus(bo.getStatus() == null ? "0" : bo.getStatus());
        category.setRemark(bo.getRemark());
        categoryMapper.insert(category);
        return toCategoryVo(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CategoryVo updateCategory(Long categoryId, CategoryBo bo) {
        OaFlowCategory category = requireCategory(categoryId);
        OaFlowCategory sameCode = categoryMapper.selectOne(new LambdaQueryWrapper<OaFlowCategory>()
            .eq(OaFlowCategory::getCode, bo.getCode()));
        if (sameCode != null && !sameCode.getId().equals(categoryId)) {
            throw new ServiceException("分类编码已存在", 409);
        }
        category.setCode(bo.getCode());
        category.setName(bo.getName());
        if (bo.getSort() != null) {
            category.setSort(bo.getSort());
        }
        if (bo.getStatus() != null) {
            category.setStatus(bo.getStatus());
        }
        category.setRemark(bo.getRemark());
        categoryMapper.updateById(category);
        return toCategoryVo(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long categoryId) {
        requireCategory(categoryId);
        Long used = definitionMapper.selectCount(new LambdaQueryWrapper<OaFlowDefinition>()
            .eq(OaFlowDefinition::getCategoryId, categoryId));
        if (used != null && used > 0) {
            throw new ServiceException("分类下存在流程定义，禁止删除", 409);
        }
        categoryMapper.deleteById(categoryId);
    }

    // ------------------------------------------------------------------ 定义查询

    @Override
    public List<DefinitionVo> selectDefinitions() {
        List<OaFlowDefinition> definitions = definitionMapper.selectList(new LambdaQueryWrapper<OaFlowDefinition>()
            .orderByAsc(OaFlowDefinition::getSort));
        return definitions.stream().map(this::toDefinitionVo).toList();
    }

    @Override
    public DefinitionVo selectDefinition(Long definitionId) {
        return toDefinitionVo(requireDefinition(definitionId));
    }

    @Override
    public LaunchCatalogVo selectLaunchCatalog() {
        List<OaFlowCategory> activeCategories = categoryMapper.selectList(new LambdaQueryWrapper<OaFlowCategory>()
            .eq(OaFlowCategory::getStatus, CATEGORY_ACTIVE)
            .orderByAsc(OaFlowCategory::getSort));
        Map<Long, String> categoryNames = new LinkedHashMap<>();
        activeCategories.forEach(category -> categoryNames.put(category.getId(), category.getName()));

        LaunchCatalogVo vo = new LaunchCatalogVo();
        vo.setCategories(activeCategories.stream().map(this::toCategoryVo).toList());
        List<OaFlowDefinition> definitions = definitionMapper.selectList(new LambdaQueryWrapper<OaFlowDefinition>()
            .eq(OaFlowDefinition::getStatus, FlowTemplateStatus.PUBLISHED.name())
            .orderByAsc(OaFlowDefinition::getSort));
        vo.setDefinitions(definitions.stream()
            .filter(definition -> definition.getCategoryId() != null
                && categoryNames.containsKey(definition.getCategoryId()))
            .map(definition -> toLaunchDefinitionVo(definition, categoryNames.get(definition.getCategoryId())))
            .toList());
        return vo;
    }

    @Override
    public LaunchDefinitionVo selectLaunchableDefinition(Long definitionId) {
        OaFlowDefinition definition = requireDefinition(definitionId);
        if (!FlowTemplateStatus.PUBLISHED.name().equals(definition.getStatus())) {
            throw new ServiceException("WF_STATE_CONFLICT 流程未启用", 409);
        }
        OaFlowCategory category = categoryMapper.selectById(definition.getCategoryId());
        if (category == null || !CATEGORY_ACTIVE.equals(category.getStatus())) {
            throw new ServiceException("WF_STATE_CONFLICT 流程分类未启用", 409);
        }
        return toLaunchDefinitionVo(definition, category.getName());
    }

    // ------------------------------------------------------------------ 定义创建

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefinitionVo createDefinition(DefinitionBo bo) {
        String processKey = bo.getProcessKey() == null ? "" : bo.getProcessKey().trim();
        if (!KEY_PATTERN.matcher(processKey).matches()) {
            throw new ServiceException("流程 Key 非法（字母开头，仅字母数字下划线）: " + processKey, 400);
        }
        if (TemplateRegistrar.isBuiltin(processKey)) {
            throw new ServiceException("流程 Key 与内置模板冲突: " + processKey, 409);
        }
        Long duplicated = definitionMapper.selectCount(new LambdaQueryWrapper<OaFlowDefinition>()
            .eq(OaFlowDefinition::getProcessKey, processKey));
        if (duplicated != null && duplicated > 0) {
            throw new ServiceException("流程 Key 已存在: " + processKey, 409);
        }
        String processName = requireProcessName(bo.getProcessName());
        Long categoryId = requireCategoryId(bo.getCategoryId());
        String businessType = normalizeBusinessType(bo.getBusinessType());

        FormDesignBo formDesign = requireForm(bo.getForm());
        String formKey = normalizeKey(formDesign.getFormKey(), "表单 Key");
        String schemaJson = serializeSchema(formDesign.getSchema());

        FlowChainCompiler.CompiledFlow compiled = compile(bo.getChain(), processKey, processName);
        String chainJson = PlainJson.toJson(bo.getChain());

        OaFlowDefinition definition = new OaFlowDefinition();
        definition.setProcessKey(processKey);
        definition.setProcessName(processName);
        definition.setCategoryId(categoryId);
        definition.setFormKey(formKey);
        definition.setBusinessType(businessType);
        definition.setCurrentVersionNo(1);
        definition.setStatus(FlowTemplateStatus.DRAFT.name());
        definition.setIcon(bo.getIcon());
        definition.setSort(bo.getSort() == null ? 0 : bo.getSort());
        definition.setRemark(bo.getRemark());
        definitionMapper.insert(definition);

        upsertFormVersion(formKey, formNameOf(formDesign), schemaJson);
        insertVersion(definition, 1, compiled, chainJson);
        return toDefinitionVo(requireDefinition(definition.getId()));
    }

    // ------------------------------------------------------------------ 定义更新

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefinitionVo updateDefinition(Long definitionId, DefinitionBo bo) {
        OaFlowDefinition definition = requireDefinition(definitionId);
        if (bo.getProcessKey() != null && !bo.getProcessKey().isBlank()
            && !bo.getProcessKey().trim().equals(definition.getProcessKey())) {
            throw new ServiceException("流程 Key 不可修改", 409);
        }
        if (bo.getProcessName() != null && !bo.getProcessName().isBlank()) {
            definition.setProcessName(requireProcessName(bo.getProcessName()));
        }
        if (bo.getCategoryId() != null) {
            definition.setCategoryId(requireCategoryId(bo.getCategoryId()));
        }
        if (bo.getBusinessType() != null && !bo.getBusinessType().isBlank()) {
            definition.setBusinessType(normalizeBusinessType(bo.getBusinessType()));
        }
        if (bo.getIcon() != null) {
            definition.setIcon(bo.getIcon());
        }
        if (bo.getSort() != null) {
            definition.setSort(bo.getSort());
        }
        if (bo.getRemark() != null) {
            definition.setRemark(bo.getRemark());
        }
        definitionMapper.updateById(definition);

        if (bo.getForm() == null && bo.getChain() == null) {
            return toDefinitionVo(requireDefinition(definitionId));
        }

        OaFlowDefinitionVersion latest = latestVersion(definitionId);
        String schemaJson = null;
        String formName = null;
        String formKey = definition.getFormKey();
        if (bo.getForm() != null) {
            FormDesignBo formDesign = bo.getForm();
            formKey = normalizeKey(formDesign.getFormKey() == null || formDesign.getFormKey().isBlank()
                ? definition.getFormKey() : formDesign.getFormKey(), "表单 Key");
            schemaJson = serializeSchema(formDesign.getSchema());
            formName = formNameOf(formDesign);
            upsertFormVersion(formKey, formName, schemaJson);
            definition.setFormKey(formKey);
            definitionMapper.updateById(definition);
        } else if (latest != null) {
            schemaJson = latestSchemaOf(formKey);
        }

        String chainJson = bo.getChain() != null
            ? PlainJson.toJson(bo.getChain())
            : (latest == null ? null : latest.getChainJson());
        FlowChainConfig chain = bo.getChain() != null ? bo.getChain()
            : (chainJson == null ? null : PlainJson.parse(chainJson, FlowChainConfig.class));
        FlowChainCompiler.CompiledFlow compiled = compile(chain, definition.getProcessKey(), definition.getProcessName());

        int nextVersion = latest == null ? 1 : latest.getVersionNo() + 1;
        insertVersion(definition, nextVersion, compiled, chainJson);
        return toDefinitionVo(requireDefinition(definitionId));
    }

    // ------------------------------------------------------------------ 启停用

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefinitionVo changeStatus(Long definitionId, DefinitionStatusBo bo) {
        OaFlowDefinition definition = requireDefinition(definitionId);
        FlowTemplateStatus target;
        try {
            target = FlowTemplateStatus.from(bo.getStatus());
        } catch (IllegalArgumentException e) {
            throw new ServiceException("未知状态: " + bo.getStatus(), 400);
        }
        switch (target) {
            case PUBLISHED -> {
                OaFlowDefinitionVersion latest = latestVersion(definitionId);
                if (latest == null) {
                    throw new ServiceException("流程定义没有可用版本", 409);
                }
                deployIfNeeded(definition, latest);
                latest.setStatus(FlowTemplateStatus.PUBLISHED.name());
                if (latest.getPublishedTime() == null) {
                    latest.setPublishedTime(new Date());
                    latest.setPublishedBy(LoginHelper.getUserId());
                }
                versionMapper.updateById(latest);
                definition.setCurrentVersionNo(latest.getVersionNo());
            }
            case RETIRED, DRAFT -> {
                // 停用/回草稿只影响定义状态，在途实例继续
            }
        }
        definition.setStatus(target.name());
        definitionMapper.updateById(definition);
        return toDefinitionVo(requireDefinition(definitionId));
    }

    // ------------------------------------------------------------------ 发布

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefinitionVo publish(Long definitionId, Integer versionNo) {
        OaFlowDefinition definition = requireDefinition(definitionId);
        int targetVersion = versionNo == null ? definition.getCurrentVersionNo() : versionNo;
        OaFlowDefinitionVersion version = versionMapper.selectOne(new LambdaQueryWrapper<OaFlowDefinitionVersion>()
            .eq(OaFlowDefinitionVersion::getDefinitionId, definitionId)
            .eq(OaFlowDefinitionVersion::getVersionNo, targetVersion));
        if (version == null) {
            throw new ServiceException("流程定义版本不存在", 404);
        }
        if (FlowTemplateStatus.RETIRED.name().equals(version.getStatus())) {
            throw new ServiceException("已下线版本不能发布", 409);
        }
        if (FlowTemplateStatus.PUBLISHED.name().equals(version.getStatus())) {
            throw new ServiceException("该版本已发布", 409);
        }
        deployIfNeeded(definition, version);
        version.setStatus(FlowTemplateStatus.PUBLISHED.name());
        version.setPublishedBy(LoginHelper.getUserId());
        version.setPublishedTime(new Date());
        versionMapper.updateById(version);

        definition.setStatus(FlowTemplateStatus.PUBLISHED.name());
        definition.setCurrentVersionNo(version.getVersionNo());
        definitionMapper.updateById(definition);
        return toDefinitionVo(requireDefinition(definitionId));
    }

    // ------------------------------------------------------------------ 内置模板一键启用

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int enableBuiltinTemplates() {
        int enabled = 0;
        for (var spec : BuiltInTemplateCatalog.all()) {
            OaFlowDefinition definition = definitionMapper.selectOne(new LambdaQueryWrapper<OaFlowDefinition>()
                .eq(OaFlowDefinition::getProcessKey, spec.getProcessKey()));
            if (definition == null) {
                continue;
            }
            if (FlowTemplateStatus.PUBLISHED.name().equals(definition.getStatus())) {
                continue;
            }
            OaFlowDefinitionVersion latest = latestVersion(definition.getId());
            if (latest == null) {
                continue;
            }
            deployIfNeeded(definition, latest);
            latest.setStatus(FlowTemplateStatus.PUBLISHED.name());
            if (latest.getPublishedTime() == null) {
                latest.setPublishedTime(new Date());
                latest.setPublishedBy(LoginHelper.getUserId());
            }
            versionMapper.updateById(latest);
            definition.setCurrentVersionNo(latest.getVersionNo());
            definition.setStatus(FlowTemplateStatus.PUBLISHED.name());
            definitionMapper.updateById(definition);
            enabled++;
        }
        return enabled;
    }

    // ------------------------------------------------------------------ 删除

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDefinition(Long definitionId) {
        OaFlowDefinition definition = requireDefinition(definitionId);
        if (TemplateRegistrar.isBuiltin(definition.getProcessKey())) {
            throw new ServiceException("WF_FORBIDDEN 内置模板禁止删除，可改为停用", 403);
        }
        Long instances = instanceMapper.selectCount(new LambdaQueryWrapper<OaFlowInstance>()
            .eq(OaFlowInstance::getDefinitionId, definitionId));
        if (instances != null && instances > 0) {
            throw new ServiceException("WF_STATE_CONFLICT 该流程定义已有 " + instances + " 条实例记录，禁止删除", 409);
        }
        versionMapper.delete(new LambdaQueryWrapper<OaFlowDefinitionVersion>()
            .eq(OaFlowDefinitionVersion::getDefinitionId, definitionId));
        definitionMapper.deleteById(definitionId);
    }

    // ------------------------------------------------------------------ BPMN / 图

    @Override
    public String selectBpmnXml(Long definitionId, Integer versionNo) {
        return BpmnSource.text(requireVersion(definitionId, versionNo));
    }

    @Override
    public byte[] definitionDiagram(Long definitionId, Integer versionNo) {
        OaFlowDefinitionVersion version = requireVersion(definitionId, versionNo);
        if (version.getFlowableProcDefId() == null) {
            throw new ServiceException("流程版本尚未部署", 409);
        }
        try {
            BpmnModel bpmnModel = repositoryService.getBpmnModel(version.getFlowableProcDefId());
            String font = DiagramFontProvider.family();
            try (InputStream in = new DefaultProcessDiagramGenerator()
                .generateDiagram(bpmnModel, "png", java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                    font, font, font, null, 1.0, false);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                return out.toByteArray();
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw WorkflowOrchestrator.translate(e);
        }
    }

    // ------------------------------------------------------------------ 表单

    @Override
    public List<FormVo> selectForms() {
        return formVersionMapper.selectList(new LambdaQueryWrapper<OaFlowFormVersion>()
                .orderByAsc(OaFlowFormVersion::getFormKey)
                .orderByDesc(OaFlowFormVersion::getVersionNo))
            .stream().map(this::toFormVo).toList();
    }

    @Override
    public FormVo selectForm(String formKey) {
        return toFormVo(requirePublishedForm(formKey));
    }

    // ------------------------------------------------------------------ 内部：编译与版本

    private FlowChainCompiler.CompiledFlow compile(FlowChainConfig chain, String processKey, String processName) {
        if (chain == null) {
            throw new ServiceException("审批链配置不能为空", 400);
        }
        // ROLE:key 角色存在性校验：替换固定白名单，只接受 sys_role 里启用的角色
        java.util.Set<String> roleKeys = new java.util.HashSet<>(identityMapper.selectEnabledRoleKeys());
        return FlowChainCompiler.compile(chain, processKey, processName, roleKeys::contains);
    }

    private void insertVersion(OaFlowDefinition definition, int versionNo,
                               FlowChainCompiler.CompiledFlow compiled, String chainJson) {
        OaFlowDefinitionVersion version = new OaFlowDefinitionVersion();
        version.setDefinitionId(definition.getId());
        version.setVersionNo(versionNo);
        version.setBpmnResource("chain://" + definition.getProcessKey() + "/v" + versionNo);
        version.setBpmnXml(compiled.bpmnXml());
        version.setChainJson(chainJson);
        version.setBpmnDigest(FlowIdempotencyGuard.digest(compiled.bpmnXml()));
        version.setValidationSummary(compiled.validationSummary());
        version.setStatus(FlowTemplateStatus.DRAFT.name());
        version.setCreateTime(new Date());
        versionMapper.insert(version);
    }

    /**
     * 部署到 Flowable：编译产物与内置模板统一走版本内联 BPMN，部署前必须再次通过受控门禁。
     * 幂等：已部署且已发布则跳过。
     */
    private void deployIfNeeded(OaFlowDefinition definition, OaFlowDefinitionVersion version) {
        if (version.getFlowableProcDefId() != null
            && FlowTemplateStatus.PUBLISHED.name().equals(version.getStatus())) {
            return;
        }
        String bpmnXml = BpmnSource.text(version);
        // 编译门禁：部署前必须仍是受控 BPMN
        BpmnTemplateValidator.validate(bpmnXml.getBytes(StandardCharsets.UTF_8));
        var deployment = repositoryService.createDeployment()
            .name("agentoa-" + definition.getProcessKey() + "-v" + version.getVersionNo())
            .key("agentoa-" + definition.getProcessKey())
            .addString(definition.getProcessKey() + ".bpmn20.xml", bpmnXml)
            .deploy();
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .deploymentId(deployment.getId()).singleResult();
        version.setFlowableDeploymentId(deployment.getId());
        version.setFlowableProcDefId(processDefinition.getId());
    }

    // ------------------------------------------------------------------ 内部：表单版本

    private FormDesignBo requireForm(FormDesignBo form) {
        if (form == null || form.getSchema() == null) {
            throw new ServiceException("表单设计不能为空", 400);
        }
        return form;
    }

    private String serializeSchema(FormSchemaBo schema) {
        return FormSchemaSupport.validateAndSerialize(schema);
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

    private String latestSchemaOf(String formKey) {
        OaFlowFormVersion latest = formVersionMapper.selectOne(new LambdaQueryWrapper<OaFlowFormVersion>()
            .eq(OaFlowFormVersion::getFormKey, formKey)
            .orderByDesc(OaFlowFormVersion::getVersionNo)
            .last("LIMIT 1"));
        return latest == null ? null : latest.getSchemaJson();
    }

    private OaFlowFormVersion requirePublishedForm(String formKey) {
        OaFlowFormVersion formVersion = formVersionMapper.selectOne(new LambdaQueryWrapper<OaFlowFormVersion>()
            .eq(OaFlowFormVersion::getFormKey, formKey)
            .eq(OaFlowFormVersion::getStatus, FlowTemplateStatus.PUBLISHED.name())
            .orderByDesc(OaFlowFormVersion::getVersionNo)
            .last("LIMIT 1"));
        if (formVersion == null) {
            throw new ServiceException("表单不存在: " + formKey, 404);
        }
        return formVersion;
    }

    private String formNameOf(FormDesignBo form) {
        return form.getFormName() == null || form.getFormName().isBlank() ? form.getFormKey() : form.getFormName();
    }

    // ------------------------------------------------------------------ 内部：校验与装配

    private String requireProcessName(String processName) {
        if (processName == null || processName.isBlank() || processName.length() > 64) {
            throw new ServiceException("流程名称不能为空且不超过 64 字", 400);
        }
        return processName.trim();
    }

    private Long requireCategoryId(Long categoryId) {
        if (categoryId == null) {
            throw new ServiceException("流程分类不能为空", 400);
        }
        requireCategory(categoryId);
        return categoryId;
    }

    private String normalizeBusinessType(String businessType) {
        String value = businessType == null || businessType.isBlank() ? "generic" : businessType.trim();
        if (!BUSINESS_TYPE_PATTERN.matcher(value).matches()) {
            throw new ServiceException("业务类型标识非法: " + value, 400);
        }
        return value;
    }

    private String normalizeKey(String key, String label) {
        if (key == null || !KEY_PATTERN.matcher(key.trim()).matches()) {
            throw new ServiceException(label + " 非法（字母开头，仅字母数字下划线）: " + key, 400);
        }
        return key.trim();
    }

    private OaFlowCategory requireCategory(Long categoryId) {
        OaFlowCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new ServiceException("流程分类不存在", 404);
        }
        return category;
    }

    private OaFlowDefinition requireDefinition(Long definitionId) {
        OaFlowDefinition definition = definitionMapper.selectById(definitionId);
        if (definition == null) {
            throw new ServiceException("流程定义不存在", 404);
        }
        return definition;
    }

    private OaFlowDefinitionVersion latestVersion(Long definitionId) {
        return versionMapper.selectOne(new LambdaQueryWrapper<OaFlowDefinitionVersion>()
            .eq(OaFlowDefinitionVersion::getDefinitionId, definitionId)
            .orderByDesc(OaFlowDefinitionVersion::getVersionNo)
            .last("LIMIT 1"));
    }

    private OaFlowDefinitionVersion requireVersion(Long definitionId, Integer versionNo) {
        OaFlowDefinition definition = requireDefinition(definitionId);
        int targetVersion = versionNo == null ? definition.getCurrentVersionNo() : versionNo;
        OaFlowDefinitionVersion version = versionMapper.selectOne(new LambdaQueryWrapper<OaFlowDefinitionVersion>()
            .eq(OaFlowDefinitionVersion::getDefinitionId, definitionId)
            .eq(OaFlowDefinitionVersion::getVersionNo, targetVersion));
        if (version == null) {
            throw new ServiceException("流程定义版本不存在", 404);
        }
        return version;
    }

    // ------------------------------------------------------------------ VO 装配

    private CategoryVo toCategoryVo(OaFlowCategory category) {
        CategoryVo vo = new CategoryVo();
        vo.setId(category.getId());
        vo.setCode(category.getCode());
        vo.setName(category.getName());
        vo.setSort(category.getSort());
        vo.setStatus(category.getStatus());
        vo.setRemark(category.getRemark());
        return vo;
    }

    private DefinitionVo toDefinitionVo(OaFlowDefinition definition) {
        DefinitionVo vo = new DefinitionVo();
        vo.setId(definition.getId());
        vo.setProcessKey(definition.getProcessKey());
        vo.setProcessName(definition.getProcessName());
        vo.setCategoryId(definition.getCategoryId());
        OaFlowCategory category = categoryMapper.selectById(definition.getCategoryId());
        vo.setCategoryName(category == null ? null : category.getName());
        vo.setFormKey(definition.getFormKey());
        vo.setBusinessType(definition.getBusinessType());
        vo.setCurrentVersionNo(definition.getCurrentVersionNo());
        vo.setStatus(definition.getStatus());
        vo.setIcon(definition.getIcon());
        vo.setSort(definition.getSort());
        vo.setRemark(definition.getRemark());
        vo.setBuiltin(TemplateRegistrar.isBuiltin(definition.getProcessKey()));
        List<OaFlowDefinitionVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<OaFlowDefinitionVersion>()
            .eq(OaFlowDefinitionVersion::getDefinitionId, definition.getId())
            .orderByAsc(OaFlowDefinitionVersion::getVersionNo));
        OaFlowDefinitionVersion latest = null;
        for (OaFlowDefinitionVersion version : versions) {
            latest = version;
            DefinitionVo.VersionVo versionVo = new DefinitionVo.VersionVo();
            versionVo.setVersionNo(version.getVersionNo());
            versionVo.setBpmnResource(version.getBpmnResource());
            versionVo.setCompiled(BpmnSource.isCompiled(version));
            versionVo.setValidationSummary(version.getValidationSummary());
            versionVo.setStatus(version.getStatus());
            versionVo.setPublishedTime(version.getPublishedTime());
            vo.getVersions().add(versionVo);
        }
        if (latest != null) {
            vo.setChain(latest.getChainJson());
            try {
                vo.setForm(toFormVo(requirePublishedForm(definition.getFormKey())));
            } catch (ServiceException e) {
                OaFlowFormVersion any = formVersionMapper.selectOne(new LambdaQueryWrapper<OaFlowFormVersion>()
                    .eq(OaFlowFormVersion::getFormKey, definition.getFormKey())
                    .orderByDesc(OaFlowFormVersion::getVersionNo)
                    .last("LIMIT 1"));
                if (any != null) {
                    vo.setForm(toFormVo(any));
                }
            }
        }
        return vo;
    }

    private FormVo toFormVo(OaFlowFormVersion formVersion) {
        FormVo vo = new FormVo();
        vo.setFormKey(formVersion.getFormKey());
        vo.setFormName(formVersion.getFormName());
        vo.setVersionNo(formVersion.getVersionNo());
        vo.setStatus(formVersion.getStatus());
        vo.setSchema(formVersion.getSchemaJson());
        return vo;
    }

    private LaunchDefinitionVo toLaunchDefinitionVo(OaFlowDefinition definition, String categoryName) {
        LaunchDefinitionVo vo = new LaunchDefinitionVo();
        vo.setId(definition.getId());
        vo.setProcessKey(definition.getProcessKey());
        vo.setProcessName(definition.getProcessName());
        vo.setCategoryId(definition.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setFormKey(definition.getFormKey());
        vo.setBusinessType(definition.getBusinessType());
        vo.setCurrentVersionNo(definition.getCurrentVersionNo());
        vo.setIcon(definition.getIcon());
        vo.setSort(definition.getSort());
        vo.setRemark(definition.getRemark());
        try {
            vo.setForm(toFormVo(requirePublishedForm(definition.getFormKey())));
        } catch (ServiceException e) {
            // 表单未发布时仍出现在目录里，前端提示「表单模板不可用」。
            vo.setForm(null);
        }
        // 发起人自选节点：从已发布审批链提取（不下发完整 chain）
        vo.getSelectableNodes().addAll(selectableNodesOf(definition));
        return vo;
    }

    private List<SelectableNodeVo> selectableNodesOf(OaFlowDefinition definition) {
        OaFlowDefinitionVersion version = versionMapper.selectOne(new LambdaQueryWrapper<OaFlowDefinitionVersion>()
            .eq(OaFlowDefinitionVersion::getDefinitionId, definition.getId())
            .eq(OaFlowDefinitionVersion::getVersionNo, definition.getCurrentVersionNo())
            .last("LIMIT 1"));
        if (version == null || version.getChainJson() == null) {
            return List.of();
        }
        return UserSelectResolver.selectableSummaries(version.getChainJson()).stream()
            .map(node -> {
                SelectableNodeVo vo = new SelectableNodeVo();
                vo.setNodeId(node.nodeId());
                vo.setName(node.name());
                vo.setMultiple(node.multiple());
                return vo;
            })
            .toList();
    }
}

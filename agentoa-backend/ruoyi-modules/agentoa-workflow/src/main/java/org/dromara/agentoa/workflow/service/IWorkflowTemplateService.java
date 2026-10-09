package org.dromara.agentoa.workflow.service;

import org.dromara.agentoa.workflow.domain.bo.CategoryBo;
import org.dromara.agentoa.workflow.domain.bo.DefinitionBo;
import org.dromara.agentoa.workflow.domain.bo.DefinitionStatusBo;
import org.dromara.agentoa.workflow.domain.vo.CategoryVo;
import org.dromara.agentoa.workflow.domain.vo.DefinitionVo;
import org.dromara.agentoa.workflow.domain.vo.FormVo;
import org.dromara.agentoa.workflow.domain.vo.LaunchCatalogVo;
import org.dromara.agentoa.workflow.domain.vo.LaunchDefinitionVo;

import java.util.List;

/**
 * 流程定义与表单管理（docs/05 section 4.1/4.2）。
 * <p>
 * 流程结构只接受结构化审批链配置（{@code DefinitionBo.chain}），由 {@code FlowChainCompiler}
 * 编译为受控 BPMN 并再过白名单门禁；不开放任意 BPMN 上传。
 */
public interface IWorkflowTemplateService {

    List<CategoryVo> selectCategories();

    CategoryVo createCategory(CategoryBo bo);

    CategoryVo updateCategory(Long categoryId, CategoryBo bo);

    void deleteCategory(Long categoryId);

    List<DefinitionVo> selectDefinitions();

    DefinitionVo selectDefinition(Long definitionId);

    /**
     * 发起申请目录（H5/移动端，登录即可读）：启用分类 + 已发布流程定义（含已发布表单快照）。
     * 与管理端 {@link #selectDefinitions()} 的区别是按「可发起」语义过滤：只返回
     * {@code status=PUBLISHED} 的定义，且停用分类下的定义一并隐藏。
     */
    LaunchCatalogVo selectLaunchCatalog();

    /** 单个可发起流程（登录即可读）：未发布或分类停用返回 409，供 H5 发起页按 definitionId 取表单快照。 */
    LaunchDefinitionVo selectLaunchableDefinition(Long definitionId);

    /** 创建定义（含表单设计 + 审批链配置），产出版本 1（DRAFT） */
    DefinitionVo createDefinition(DefinitionBo bo);

    /** 更新展示元数据；form/chain 任一变化都会产出新版本（版本发布后不可变） */
    DefinitionVo updateDefinition(Long definitionId, DefinitionBo bo);

    /** 启用/停用：PUBLISHED=启用、RETIRED=停用、DRAFT=草稿 */
    DefinitionVo changeStatus(Long definitionId, DefinitionStatusBo bo);

    DefinitionVo publish(Long definitionId, Integer versionNo);

    /** 删除定义（内置模板与已有实例的定义受保护） */
    void deleteDefinition(Long definitionId);

    /** 一键启用全部内置模板，返回本次启用数量 */
    int enableBuiltinTemplates();

    String selectBpmnXml(Long definitionId, Integer versionNo);

    byte[] definitionDiagram(Long definitionId, Integer versionNo);

    List<FormVo> selectForms();

    FormVo selectForm(String formKey);
}

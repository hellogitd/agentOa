package org.dromara.agentoa.workflow.service.support;

import lombok.extern.slf4j.Slf4j;
import org.dromara.agentoa.workflow.domain.template.BuiltInTemplateSpec;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置模板目录：从 {@code workflow/templates/builtin-templates.json} 载入 26 个内置模板，
 * 每个模板都带「表单设计 + 推荐默认审批链」，与用户自定义流程走同一套编译与校验路径。
 * <p>
 * 目录只读、进程内单例；载入即做结构校验，链配置的完整性由 {@code FlowChainCompiler} 在注册时把关。
 */
@Slf4j
public final class BuiltInTemplateCatalog {

    /** 内置模板清单资源 */
    public static final String CATALOG_RESOURCE = "workflow/templates/builtin-templates.json";

    private static final Object LOCK = new Object();

    private static volatile List<BuiltInTemplateSpec> cached;
    private static volatile Map<String, BuiltInTemplateSpec> index;

    private BuiltInTemplateCatalog() {
    }

    /** 全部内置模板（按 sort, processKey 稳定排序） */
    public static List<BuiltInTemplateSpec> all() {
        load();
        return cached;
    }

    /** 按流程 Key 取模板，不存在返回 null */
    public static BuiltInTemplateSpec of(String processKey) {
        load();
        return processKey == null ? null : index.get(processKey);
    }

    /** 是否为内置模板 Key */
    public static boolean isBuiltin(String processKey) {
        return of(processKey) != null;
    }

    /** 内置模板数量 */
    public static int size() {
        return all().size();
    }

    private static void load() {
        List<BuiltInTemplateSpec> local = cached;
        if (local != null) {
            return;
        }
        synchronized (LOCK) {
            if (cached == null) {
                cached = Collections.unmodifiableList(readAndValidate());
                Map<String, BuiltInTemplateSpec> map = new LinkedHashMap<>();
                for (BuiltInTemplateSpec spec : cached) {
                    map.put(spec.getProcessKey(), spec);
                }
                index = Collections.unmodifiableMap(map);
                log.info("内置流程模板载入完成: {} 个", cached.size());
            }
        }
    }

    private static List<BuiltInTemplateSpec> readAndValidate() {
        byte[] bytes;
        try (InputStream in = new ClassPathResource(CATALOG_RESOURCE).getInputStream()) {
            bytes = in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("内置模板清单不存在: " + CATALOG_RESOURCE, e);
        }
        List<BuiltInTemplateSpec> specs = PlainJson.parseList(
            new String(bytes, StandardCharsets.UTF_8), BuiltInTemplateSpec.class);
        if (specs == null || specs.isEmpty()) {
            throw new IllegalStateException("内置模板清单为空: " + CATALOG_RESOURCE);
        }
        Map<String, Boolean> seen = new LinkedHashMap<>();
        List<BuiltInTemplateSpec> result = new ArrayList<>(specs.size());
        for (BuiltInTemplateSpec spec : specs) {
            validateSpec(spec);
            if (seen.put(spec.getProcessKey(), Boolean.TRUE) != null) {
                throw new IllegalStateException("内置模板流程 Key 重复: " + spec.getProcessKey());
            }
            result.add(spec);
        }
        result.sort((a, b) -> {
            int cmp = Integer.compare(a.getSort() == null ? 0 : a.getSort(), b.getSort() == null ? 0 : b.getSort());
            return cmp != 0 ? cmp : a.getProcessKey().compareTo(b.getProcessKey());
        });
        return result;
    }

    private static void validateSpec(BuiltInTemplateSpec spec) {
        if (spec == null || spec.getProcessKey() == null || spec.getProcessKey().isBlank()) {
            throw new IllegalStateException("内置模板缺少 processKey");
        }
        if (spec.getProcessName() == null || spec.getProcessName().isBlank()) {
            throw new IllegalStateException("内置模板缺少 processName: " + spec.getProcessKey());
        }
        if (spec.getFormKey() == null || spec.getFormKey().isBlank()) {
            throw new IllegalStateException("内置模板缺少 formKey: " + spec.getProcessKey());
        }
        if (spec.getCategoryId() == null) {
            throw new IllegalStateException("内置模板缺少 categoryId: " + spec.getProcessKey());
        }
        if (spec.getBusinessType() == null || spec.getBusinessType().isBlank()) {
            spec.setBusinessType(spec.getProcessKey());
        }
        if (spec.getFormName() == null || spec.getFormName().isBlank()) {
            spec.setFormName(spec.getProcessName());
        }
        try {
            FormSchemaSupport.validateAndSerialize(spec.getForm());
        } catch (ServiceException e) {
            throw new IllegalStateException("内置模板表单非法 (" + spec.getProcessKey() + "): " + e.getMessage(), e);
        }
        if (spec.getChain() == null || spec.getChain().getNodes() == null || spec.getChain().getNodes().isEmpty()) {
            throw new IllegalStateException("内置模板缺少推荐审批链: " + spec.getProcessKey());
        }
    }
}

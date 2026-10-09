package org.dromara.agentoa.workflow.service.support;

import org.dromara.agentoa.workflow.domain.OaFlowDefinitionVersion;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * BPMN 来源解析：代码仓库模板走 classpath 资源，编译产物走版本行内联 XML。
 * 两条路径统一在这里收口，禁止调用方各自猜测。
 */
public final class BpmnSource {

    private BpmnSource() {
    }

    /** 取版本对应的 BPMN 字节 */
    public static byte[] bytes(OaFlowDefinitionVersion version) {
        if (version == null) {
            throw new ServiceException("流程定义版本不存在", 404);
        }
        if (version.getBpmnXml() != null && !version.getBpmnXml().isBlank()) {
            return version.getBpmnXml().getBytes(StandardCharsets.UTF_8);
        }
        String resource = version.getBpmnResource();
        if (resource == null || resource.isBlank()) {
            throw new ServiceException("流程定义版本缺少 BPMN 内容", 500);
        }
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new ServiceException("BPMN 资源读取失败: " + resource, 500);
        }
    }

    /** 取版本对应的 BPMN 文本 */
    public static String text(OaFlowDefinitionVersion version) {
        return new String(bytes(version), StandardCharsets.UTF_8);
    }

    /** 是否为编译产物（内联 XML） */
    public static boolean isCompiled(OaFlowDefinitionVersion version) {
        return version != null && version.getBpmnXml() != null && !version.getBpmnXml().isBlank();
    }
}

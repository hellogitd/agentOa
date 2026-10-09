package org.dromara.agentoa.workflow.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.agentoa.workflow.assigner.AssigneeResolutionException;
import org.dromara.agentoa.workflow.assigner.AssigneeResolver;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多实例集合解析（WF-08）：流程启动时按受控规则解析各多实例节点的候选集合并注入流程变量。
 * 集合来源仅允许 AssigneeResolver 的 ROLE:key/WHITELIST 候选集，或 USER_SELECT（发起人自选，
 * 取自 {@code userSelect_<nodeId>} 流程变量）；禁 SELF/LEADER 单人规则，解析失败整体回滚 400。
 */
@Component
@RequiredArgsConstructor
public class MiCollectionResolver {

    private final AssigneeResolver assigneeResolver;

    /** 解析 BPMN 中全部多实例 userTask 的集合变量：变量名 -> List<String> userId */
    public Map<String, Object> resolveCollections(String bpmnResource, Long initiatorUserId, Long initiatorDeptId) {
        return resolveCollections(readResourceBytes(bpmnResource), initiatorUserId, initiatorDeptId, Map.of());
    }

    /** 解析 BPMN 字节（编译产物/内联 XML）中全部多实例 userTask 的集合变量 */
    public Map<String, Object> resolveCollections(byte[] bpmnBytes, Long initiatorUserId, Long initiatorDeptId) {
        return resolveCollections(bpmnBytes, initiatorUserId, initiatorDeptId, Map.of());
    }

    /**
     * @param precomputed 已计算的流程变量（USER_SELECT 集合取 {@code userSelect_<nodeId>}）
     */
    public Map<String, Object> resolveCollections(byte[] bpmnBytes, Long initiatorUserId, Long initiatorDeptId,
                                                  Map<String, Object> precomputed) {
        Document doc = BpmnTemplateValidator.parseSecurely(bpmnBytes);
        Map<String, Object> variables = new LinkedHashMap<>();
        Map<String, Object> source = precomputed == null ? Map.of() : precomputed;
        NodeList userTasks = doc.getElementsByTagNameNS("*", "userTask");
        for (int i = 0; i < userTasks.getLength(); i++) {
            Element userTask = (Element) userTasks.item(i);
            Element multiInstance = findChild(userTask, "multiInstanceLoopCharacteristics");
            if (multiInstance == null) {
                continue;
            }
            String collection = attributeValue(multiInstance, "collection");
            if (collection == null) {
                collection = attributeValue(userTask, "collection");
            }
            String varName = collection == null ? null
                : collection.trim().replace("${", "").replace("}", "").trim();
            if (varName == null || !varName.matches("[A-Za-z][A-Za-z0-9_]*")) {
                throw new ServiceException("BPMN 多实例缺少 flowable:collection 变量绑定", 400);
            }
            String rule = BpmnTemplateValidator.findRule(userTask);
            AssigneeResolver.AssigneeSet set;
            if (AssigneeResolver.RULE_USER_SELECT.equals(rule)) {
                Object selection = source.get(AssigneeResolver.USER_SELECT_VARIABLE_PREFIX + userTask.getAttribute("id"));
                try {
                    set = assigneeResolver.resolveUserSelect(selection);
                } catch (AssigneeResolutionException e) {
                    throw new ServiceException(e.getMessage(), 400);
                }
            } else if (rule != null && (rule.startsWith("ROLE:") || rule.startsWith("WHITELIST:"))) {
                try {
                    set = assigneeResolver.resolve(rule, initiatorUserId, initiatorDeptId);
                } catch (AssigneeResolutionException e) {
                    throw new ServiceException(e.getMessage(), 400);
                }
            } else {
                throw new ServiceException("BPMN 多实例集合来源仅允许 ROLE:key/WHITELIST/USER_SELECT: " + rule, 400);
            }
            List<Long> users = set.allUserIds();
            if (users.isEmpty()) {
                throw new ServiceException("BPMN 多实例集合为空: " + rule, 400);
            }
            List<String> ids = new ArrayList<>();
            for (Long userId : users) {
                ids.add(String.valueOf(userId));
            }
            variables.put(varName, ids);
        }
        return variables;
    }

    private Document readResource(String bpmnResource) {
        return BpmnTemplateValidator.parseSecurely(readResourceBytes(bpmnResource));
    }

    private byte[] readResourceBytes(String bpmnResource) {
        try (InputStream in = new ClassPathResource(bpmnResource).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new ServiceException("模板资源不存在: " + bpmnResource, 500);
        }
    }

    private static Element findChild(Element parent, String local) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element
                && (element.getLocalName() != null ? element.getLocalName() : element.getNodeName()).equals(local)) {
                return element;
            }
        }
        return null;
    }

    private static String attributeValue(Element element, String localName) {
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Node attr = element.getAttributes().item(i);
            String name = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
            if (name.equals(localName) && !attr.getNodeName().startsWith("xmlns")) {
                return attr.getNodeValue();
            }
        }
        return null;
    }
}

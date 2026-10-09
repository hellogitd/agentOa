package org.dromara.agentoa.workflow.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 受控 BPMN 校验（docs/03 section 5）：固定元素白名单、固定监听器类、白名单条件表达式；禁用外部实体。
 * P1 批次二（WF-08）扩展受控多实例：multiInstanceLoopCharacteristics（仅并行）+ 白名单 completionCondition
 * + flowable:collection/elementVariable（集合仅允许 AssigneeResolver 候选集规则 ROLE:key/WHITELIST）。
 */
public final class BpmnTemplateValidator {

    private static final Set<String> ALLOWED_ELEMENTS = Set.of(
        "definitions", "process", "startEvent", "endEvent", "userTask", "exclusiveGateway",
        "sequenceFlow", "conditionExpression", "extensionElements", "taskListener", "field", "string", "documentation",
        "multiInstanceLoopCharacteristics", "loopCardinality", "completionCondition", "inputDataItem", "outputDataItem",
        "BPMNDiagram", "BPMNPlane", "BPMNShape", "BPMNEdge", "Bounds", "waypoint");

    private static final Set<String> ALLOWED_TASK_ATTRIBUTES = Set.of("id", "name");

    /** 多实例受控属性（flowable 命名空间：collection/elementVariable；无命名空间：isSequential） */
    private static final Set<String> ALLOWED_MI_ATTRIBUTES = Set.of("isSequential", "collection", "elementVariable");

    private static final String MI_ELEMENT_VARIABLE = "assignee";

    private static final Set<String> ALLOWED_LISTENER_CLASS = Set.of("org.dromara.agentoa.workflow.assigner.AssigneeTaskListener");

    /**
     * 受控办理人规则（docs/12）：SELF / LEADER / DEPT_HEAD / USER_SELECT /
     * ROLE:&#123;key&#125;（key 按 {@code [a-z][a-z0-9_]*}，存在性由编译期校验）/ WHITELIST:id,id。
     * 结构化白名单，不允许任意表达式。
     */
    private static final java.util.regex.Pattern ALLOWED_RULE_PATTERN = java.util.regex.Pattern.compile(
        "SELF|LEADER|DEPT_HEAD|USER_SELECT|ROLE:[a-z][a-z0-9_]{0,31}|WHITELIST:\\d{1,19}(,\\d{1,19})*");

    /** 多实例仅允许集合型候选规则（禁 SELF/LEADER 单人规则做 MI） */
    private static final Set<String> ALLOWED_MI_RULES = Set.of("USER_SELECT");

    /**
     * 受控条件表达式白名单（正则全匹配）：只允许金额阈值比较及其取反，
     * 例如 {@code ${amount_le_1000}}、{@code ${!amount_gt_50000}}。
     * 变量名由 {@code FlowChainCompiler} 按 {@code field_op_阈值} 生成，运行时由
     * {@code ConditionVariableResolver} 求值，禁止任意表达式/SpEL/方法调用。
     */
    private static final java.util.regex.Pattern ALLOWED_CONDITION_PATTERN =
        java.util.regex.Pattern.compile("\\$\\{!?amount_(gt|ge|lt|le|eq|ne)_\\d{1,10}}");

    /** 多实例完成条件白名单：会签（全员同意）与或签（任一同意）两条 */
    private static final Set<String> ALLOWED_MI_CONDITIONS = Set.of(
        "${nrOfCompletedInstances == nrOfInstances}",
        "${nrOfCompletedInstances >= 1}");

    private BpmnTemplateValidator() {
    }

    /** 返回校验摘要；不合法模板直接拒绝注册。 */
    public static String validate(byte[] xmlBytes) {
        Document doc = parseSecurely(xmlBytes);
        Element definitions = doc.getDocumentElement();
        if (!"definitions".equals(localName(definitions))) {
            throw new ServiceException("BPMN 根元素必须是 definitions", 400);
        }
        List<String> summaries = new ArrayList<>();
        int userTasks = 0;
        int gateways = 0;
        NodeList all = definitions.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element element = (Element) all.item(i);
            String name = localName(element);
            if ("taskListener".equals(name)) {
                String clazz = element.getAttribute("class");
                if (!ALLOWED_LISTENER_CLASS.contains(clazz)) {
                    throw new ServiceException("BPMN 引用了未授权的监听器类: " + clazz, 400);
                }
                continue;
            }
            if ("conditionExpression".equals(name)) {
                String condition = element.getTextContent().trim();
                if (!isAllowedCondition(condition)) {
                    throw new ServiceException("BPMN 条件表达式不在白名单: " + condition, 400);
                }
                continue;
            }
            if ("multiInstanceLoopCharacteristics".equals(name)) {
                validateMultiInstance(element);
                continue;
            }
            if ("completionCondition".equals(name)) {
                String condition = element.getTextContent().trim();
                if (!ALLOWED_MI_CONDITIONS.contains(condition)) {
                    throw new ServiceException("BPMN 多实例完成条件不在白名单: " + condition, 400);
                }
                continue;
            }
            if ("loopCardinality".equals(name)) {
                String cardinality = element.getTextContent().trim();
                if (!cardinality.matches("\\d+")) {
                    throw new ServiceException("BPMN loopCardinality 仅允许数字字面量: " + cardinality, 400);
                }
                continue;
            }
            if ("inputDataItem".equals(name) || "outputDataItem".equals(name)) {
                validateDataItem(element);
                continue;
            }
            if (!ALLOWED_ELEMENTS.contains(name)) {
                throw new ServiceException("BPMN 包含未授权元素: " + name, 400);
            }
            if ("userTask".equals(name)) {
                userTasks++;
                boolean multiInstance = findChild(element, "multiInstanceLoopCharacteristics") != null;
                for (int a = 0; a < element.getAttributes().getLength(); a++) {
                    Node attr = element.getAttributes().item(a);
                    String attrName = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
                    if (attr.getNodeName().startsWith("xmlns") || "http://www.w3.org/2000/xmlns/".equals(attr.getNamespaceURI())) {
                        continue;
                    }
                    if (ALLOWED_TASK_ATTRIBUTES.contains(attrName) || "assigneeRule".equals(attrName)) {
                        continue;
                    }
                    if (multiInstance && ALLOWED_MI_ATTRIBUTES.contains(attrName)) {
                        continue;
                    }
                    throw new ServiceException("BPMN userTask 包含未授权属性: " + attrName, 400);
                }
                String rule = findRule(element);
                if (rule == null || !isAllowedRule(rule)) {
                    throw new ServiceException("BPMN userTask 办理人规则不在白名单: " + rule, 400);
                }
                if (multiInstance && !isCollectionRule(rule)) {
                    throw new ServiceException("BPMN 多实例 userTask 仅允许集合型规则（ROLE:key/WHITELIST/USER_SELECT）: " + rule, 400);
                }
                summaries.add("userTask:" + rule + (multiInstance ? "(MI)" : ""));
            }
            if ("exclusiveGateway".equals(name)) {
                gateways++;
            }
        }
        return "userTasks=" + userTasks + ",gateways=" + gateways + ",rules=" + summaries;
    }

    /** 多实例：仅并行（isSequential=false），collection 必须为变量绑定，elementVariable 固定 assignee */
    private static void validateMultiInstance(Element element) {
        String sequential = null;
        for (int a = 0; a < element.getAttributes().getLength(); a++) {
            Node attr = element.getAttributes().item(a);
            String attrName = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
            if (attr.getNodeName().startsWith("xmlns") || "http://www.w3.org/2000/xmlns/".equals(attr.getNamespaceURI())) {
                continue;
            }
            if ("isSequential".equals(attrName)) {
                sequential = attr.getNodeValue();
                continue;
            }
            if ("collection".equals(attrName)) {
                if (!attr.getNodeName().startsWith("flowable:")) {
                    throw new ServiceException("BPMN flowable:collection 必须使用 flowable 命名空间", 400);
                }
                if (!attr.getNodeValue().trim().matches("\\$\\{[A-Za-z][A-Za-z0-9_]*}|[A-Za-z][A-Za-z0-9_]*")) {
                    throw new ServiceException("BPMN flowable:collection 仅允许变量绑定: " + attr.getNodeValue(), 400);
                }
                continue;
            }
            if ("elementVariable".equals(attrName)) {
                if (!attr.getNodeName().startsWith("flowable:")) {
                    throw new ServiceException("BPMN flowable:elementVariable 必须使用 flowable 命名空间", 400);
                }
                if (!MI_ELEMENT_VARIABLE.equals(attr.getNodeValue().trim())) {
                    throw new ServiceException("BPMN flowable:elementVariable 仅允许 assignee", 400);
                }
                continue;
            }
            throw new ServiceException("BPMN multiInstanceLoopCharacteristics 包含未授权属性: " + attrName, 400);
        }
        if (!"false".equals(sequential)) {
            throw new ServiceException("BPMN 多实例仅允许并行（isSequential=false）", 400);
        }
    }

    private static void validateDataItem(Element element) {
        for (int a = 0; a < element.getAttributes().getLength(); a++) {
            Node attr = element.getAttributes().item(a);
            String attrName = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
            if (attr.getNodeName().startsWith("xmlns") || "http://www.w3.org/2000/xmlns/".equals(attr.getNamespaceURI())) {
                continue;
            }
            if (!Set.of("id", "name", "itemSubjectRef").contains(attrName)) {
                throw new ServiceException("BPMN " + localName(element) + " 包含未授权属性: " + attrName, 400);
            }
        }
    }

    private static Element findChild(Element parent, String local) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && local.equals(localName(element))) {
                return element;
            }
        }
        return null;
    }

    private static boolean isAllowedRule(String rule) {
        return rule != null && ALLOWED_RULE_PATTERN.matcher(rule.trim()).matches();
    }

    /** 多实例集合型规则：ROLE:key / WHITELIST / USER_SELECT */
    static boolean isCollectionRule(String rule) {
        if (rule == null) {
            return false;
        }
        String normalized = rule.trim();
        return normalized.startsWith("ROLE:") || normalized.startsWith("WHITELIST:") || ALLOWED_MI_RULES.contains(normalized);
    }

    /** 受控条件表达式：金额阈值比较及其取反 */
    static boolean isAllowedCondition(String condition) {
        return condition != null && ALLOWED_CONDITION_PATTERN.matcher(condition).matches();
    }

    static String findRule(Element userTask) {
        NodeList fields = userTask.getElementsByTagNameNS("*", "field");
        for (int i = 0; i < fields.getLength(); i++) {
            Element field = (Element) fields.item(i);
            if ("rule".equals(field.getAttribute("name"))) {
                NodeList strings = field.getElementsByTagNameNS("*", "string");
                if (strings.getLength() > 0) {
                    return strings.item(0).getTextContent().trim();
                }
            }
        }
        return null;
    }

    static Document parseSecurely(byte[] xmlBytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder builder = factory.newDocumentBuilder();
            try (InputStream in = new ByteArrayInputStream(xmlBytes)) {
                return builder.parse(in);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("BPMN 解析失败: " + e.getMessage(), 400);
        }
    }

    private static String localName(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getNodeName();
    }

    public static byte[] utf8(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }
}

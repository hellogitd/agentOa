package org.dromara.agentoa.workflow.service.support;

import org.dromara.agentoa.workflow.domain.chain.FlowChainConfig;
import org.dromara.agentoa.workflow.domain.chain.FlowChainNode;
import org.dromara.agentoa.workflow.domain.chain.FlowCondition;
import org.dromara.agentoa.workflow.domain.enums.FlowSignMode;
import org.dromara.common.core.exception.ServiceException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 审批链编译器：结构化审批链配置 → 受控 BPMN 2.0（含 BPMN DI）。
 * <p>
 * 这是唯一的流程结构入口（不开放 BPMN 上传）。编译产物必须再次通过
 * {@link BpmnTemplateValidator} 的受控白名单门禁，任何越界直接拒绝。
 * <p>
 * 节点语义：
 * <ul>
 *   <li>{@code approve} → userTask；COUNTERSIGN/EITHERSIGN → 并行多实例 + 白名单完成条件</li>
 *   <li>{@code branch} → 一对 exclusiveGateway（拆分/汇聚）+ 白名单条件表达式</li>
 *   <li>{@code cc} → 不进入 BPMN 图，收集为抄送规则，实例启动时生成 {@code oa_flow_cc} 记录
 *       （保持 BPMN 元素白名单不扩张，抄送仍是审批链配置的一等节点）</li>
 * </ul>
 * 办理人规则（docs/12）：
 * <ul>
 *   <li>{@code SELF / LEADER / DEPT_HEAD} — 单人规则</li>
 *   <li>{@code ROLE:key} — 角色候选集；key 按 {@code [a-z][a-z0-9_]*} 校验，角色存在性由调用方
 *       传入的 {@code roleExists} 钩子校验（未提供时退回内置角色白名单）</li>
 *   <li>{@code WHITELIST:id,id} — 指定成员候选集</li>
 *   <li>{@code USER_SELECT} — 发起人自选；SINGLE 选 1 人、COUNTERSIGN/EITHERSIGN 选 ≥1 人，
 *       选择结果走流程变量 {@code userSelect_<nodeId>}，缺失确定性报错不回退任意人</li>
 * </ul>
 */
public final class FlowChainCompiler {

    /** 链配置结构版本 */
    public static final int SUPPORTED_CHAIN_VERSION = 1;

    /** 发起人自选办理人规则 */
    public static final String RULE_USER_SELECT = "USER_SELECT";

    /** 角色规则 key 格式（存在性校验交给 roleExists 钩子） */
    private static final Pattern ROLE_KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    /** 未提供 roleExists 钩子时的兜底角色白名单（离线编译/单元测试） */
    private static final Set<String> FALLBACK_ROLES = Set.of(
        "ROLE:hr", "ROLE:finance", "ROLE:cashier", "ROLE:director", "ROLE:dept_manager",
        "ROLE:admin", "ROLE:gm");

    private static final Set<String> ALLOWED_SIMPLE_RULES = Set.of("SELF", "LEADER", "DEPT_HEAD");

    private static final Pattern RULE_PATTERN = Pattern.compile(
        "SELF|LEADER|DEPT_HEAD|USER_SELECT|ROLE:[a-z][a-z0-9_]{0,31}|WHITELIST:\\d{1,19}(,\\d{1,19})*");

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    private static final Set<String> ALLOWED_CONDITION_FIELDS = Set.of("amount");

    private static final Set<String> ALLOWED_CONDITION_OPS = Set.of("gt", "ge", "lt", "le", "eq", "ne");

    private static final int MAX_NODES_PER_LEVEL = 24;
    private static final int MAX_TOTAL_TASKS = 20;
    private static final int MAX_BRANCHES = 5;
    private static final int MAX_BRANCH_DEPTH = 3;
    private static final int MAX_BRANCH_NODES = 10;

    private static final double TASK_W = 100;
    private static final double TASK_H = 80;
    private static final double GW_W = 40;
    private static final double GW_H = 40;
    private static final double EVENT_W = 30;
    private static final double EVENT_H = 30;
    private static final int H_STEP = 200;
    private static final int V_STEP = 160;
    private static final int BASE_Y = 150;
    private static final int BASE_X = 120;

    private FlowChainCompiler() {
    }

    /** 抄送规则（不进入 BPMN，实例启动时生成抄送记录） */
    public record CcRule(String name, String rule) {
    }

    /** 编译产物 */
    public record CompiledFlow(String bpmnXml, List<CcRule> ccRules, String validationSummary) {
    }

    /** 一段子图的出口端口 */
    private record Port(int nextX, String ref, double x, double y) {
    }

    public static CompiledFlow compile(FlowChainConfig config, String processKey, String processName) {
        return compile(config, processKey, processName, null);
    }

    /**
     * @param roleExists {@code ROLE:key} 角色存在性校验钩子；为空时退回 {@link #FALLBACK_ROLES}
     */
    public static CompiledFlow compile(FlowChainConfig config, String processKey, String processName,
                                       Predicate<String> roleExists) {
        if (config == null) {
            throw new ServiceException("审批链配置不能为空", 400);
        }
        if (config.getChainVersion() != null && config.getChainVersion() != SUPPORTED_CHAIN_VERSION) {
            throw new ServiceException("不支持的审批链结构版本: " + config.getChainVersion(), 400);
        }
        if (processKey == null || !ID_PATTERN.matcher(processKey).matches()) {
            throw new ServiceException("流程 Key 非法: " + processKey, 400);
        }
        if (processName == null || processName.isBlank() || processName.length() > 64) {
            throw new ServiceException("流程名称非法", 400);
        }
        List<FlowChainNode> nodes = config.getNodes() == null ? List.of() : config.getNodes();
        if (nodes.isEmpty()) {
            throw new ServiceException("审批链至少需要一个节点", 400);
        }
        Ctx ctx = new Ctx();
        ctx.processKey = processKey;
        ctx.roleExists = roleExists;
        validateSequence(nodes, 0, ctx);

        ctx.elements.add(new Element("start", "startEvent", "发起", BASE_X, BASE_Y, EVENT_W, EVENT_H));
        Port exit = emitSequence(nodes, BASE_X + H_STEP, BASE_Y, 1, ctx, "start", null, null);
        ctx.elements.add(new Element("end", "endEvent", "结束", exit.nextX(), BASE_Y, EVENT_W, EVENT_H));
        ctx.edges.add(new Edge("flow_to_end", exit.ref(), "end", null, null));

        String xml = render(processKey, processName, ctx);
        String summary = BpmnTemplateValidator.validate(xml.getBytes(StandardCharsets.UTF_8));
        return new CompiledFlow(xml, List.copyOf(ctx.ccRules), summary);
    }

    // ------------------------------------------------------------------ 校验

    private static void validateSequence(List<FlowChainNode> nodes, int depth, Ctx ctx) {
        if (depth > MAX_BRANCH_DEPTH) {
            throw new ServiceException("审批链条件分支嵌套过深（最多 " + MAX_BRANCH_DEPTH + " 层）", 400);
        }
        if (nodes == null) {
            return;
        }
        if (nodes.size() > MAX_NODES_PER_LEVEL) {
            throw new ServiceException("审批链单层节点数量超过上限 " + MAX_NODES_PER_LEVEL, 400);
        }
        for (FlowChainNode node : nodes) {
            validateNode(node, depth, ctx);
        }
    }

    private static void validateNode(FlowChainNode node, int depth, Ctx ctx) {
        if (node == null) {
            throw new ServiceException("审批链节点不能为空", 400);
        }
        if (node.getId() == null || !ID_PATTERN.matcher(node.getId()).matches()) {
            throw new ServiceException("审批链节点 ID 非法: " + node.getId(), 400);
        }
        if (!ctx.ids.add(node.getId())) {
            throw new ServiceException("审批链节点 ID 重复: " + node.getId(), 400);
        }
        if (node.getName() == null || node.getName().isBlank() || node.getName().length() > 64) {
            throw new ServiceException("审批链节点名称非法: " + node.getId(), 400);
        }
        String type = node.getType() == null ? "" : node.getType();
        switch (type) {
            case FlowChainNode.TYPE_APPROVE -> {
                validateRule(node.getAssigneeRule(), node.getId(), ctx);
                FlowSignMode mode = FlowSignMode.from(node.getSignMode());
                if (mode != FlowSignMode.SINGLE) {
                    String rule = node.getAssigneeRule().trim();
                    if (!(rule.startsWith("ROLE:") || rule.startsWith("WHITELIST:") || isUserSelect(rule))) {
                        throw new ServiceException("会签/或签节点仅允许集合型规则（ROLE:key、WHITELIST 或 USER_SELECT）: " + node.getId(), 400);
                    }
                }
                ctx.taskCount++;
                if (ctx.taskCount > MAX_TOTAL_TASKS) {
                    throw new ServiceException("审批链审批节点数量超过上限 " + MAX_TOTAL_TASKS, 400);
                }
                ctx.rules.add(node.getAssigneeRule().trim() + (mode == FlowSignMode.SINGLE ? "" : "(MI)"));
                ctx.userTasks.add(node);
            }
            case FlowChainNode.TYPE_CC -> {
                validateRule(node.getAssigneeRule(), node.getId(), ctx);
                if (isUserSelect(node.getAssigneeRule())) {
                    throw new ServiceException("抄送节点不支持发起人自选规则: " + node.getId(), 400);
                }
                ctx.ccRules.add(new CcRule(node.getName(), node.getAssigneeRule().trim()));
            }
            case FlowChainNode.TYPE_BRANCH -> validateBranch(node, depth, ctx);
            default -> throw new ServiceException("未知审批链节点类型: " + type, 400);
        }
    }

    private static void validateBranch(FlowChainNode node, int depth, Ctx ctx) {
        List<FlowChainNode.FlowBranch> branches = node.getBranches() == null ? List.of() : node.getBranches();
        if (branches.isEmpty() || branches.size() > MAX_BRANCHES) {
            throw new ServiceException("条件分支数量必须在 1~" + MAX_BRANCHES + " 之间: " + node.getId(), 400);
        }
        int defaults = 0;
        for (FlowChainNode.FlowBranch branch : branches) {
            if (branch == null) {
                throw new ServiceException("条件分支不能为空", 400);
            }
            if (branch.getId() == null || !ID_PATTERN.matcher(branch.getId()).matches()) {
                throw new ServiceException("条件分支 ID 非法: " + branch.getId(), 400);
            }
            if (!ctx.ids.add(branch.getId())) {
                throw new ServiceException("条件分支 ID 重复: " + branch.getId(), 400);
            }
            if (branch.getCondition() == null || branch.getCondition().isDefaultElse()) {
                defaults++;
            } else {
                validateCondition(branch.getCondition(), branch.getId());
            }
            List<FlowChainNode> inner = branch.getNodes() == null ? List.of() : branch.getNodes();
            if (inner.size() > MAX_BRANCH_NODES) {
                throw new ServiceException("条件分支内节点数量超过上限 " + MAX_BRANCH_NODES + ": " + branch.getId(), 400);
            }
            validateSequence(inner, depth + 1, ctx);
        }
        if (defaults > 1) {
            throw new ServiceException("条件分支最多只能有一个默认分支: " + node.getId(), 400);
        }
        ctx.gatewayCount++;
    }

    private static void validateCondition(FlowCondition condition, String ownerId) {
        if (!ALLOWED_CONDITION_FIELDS.contains(condition.getField())) {
            throw new ServiceException("条件字段不在白名单: " + condition.getField() + " (" + ownerId + ")", 400);
        }
        if (!ALLOWED_CONDITION_OPS.contains(condition.getOp())) {
            throw new ServiceException("条件算子不在白名单: " + condition.getOp() + " (" + ownerId + ")", 400);
        }
        Number value = condition.getValue();
        if (value == null) {
            throw new ServiceException("条件阈值不能为空: " + ownerId, 400);
        }
        if (value.doubleValue() != Math.rint(value.doubleValue())
            || value.longValue() < 0 || value.longValue() > 1_000_000_000L) {
            throw new ServiceException("条件阈值必须是 0~1000000000 的整数: " + ownerId, 400);
        }
    }

    /** 办理人规则是否为发起人自选 */
    public static boolean isUserSelect(String rule) {
        return rule != null && RULE_USER_SELECT.equals(rule.trim());
    }

    private static void validateRule(String rule, String ownerId, Ctx ctx) {
        if (rule == null || rule.isBlank()) {
            throw new ServiceException("审批链节点缺少办理人规则: " + ownerId, 400);
        }
        String normalized = rule.trim();
        if (!RULE_PATTERN.matcher(normalized).matches()) {
            throw new ServiceException("办理人规则格式非法: " + normalized + " (" + ownerId + ")", 400);
        }
        if (normalized.startsWith("WHITELIST:")) {
            return;
        }
        if (isUserSelect(normalized)) {
            return;
        }
        if (normalized.startsWith("ROLE:")) {
            if (!ROLE_KEY_PATTERN.matcher(normalized.substring("ROLE:".length())).matches()) {
                throw new ServiceException("办理人角色格式非法: " + normalized + " (" + ownerId + ")", 400);
            }
            if (ctx.roleExists != null) {
                if (!ctx.roleExists.test(normalized.substring("ROLE:".length()))) {
                    throw new ServiceException("办理人角色不存在: " + normalized + " (" + ownerId + ")", 400);
                }
            } else if (!FALLBACK_ROLES.contains(normalized)) {
                throw new ServiceException("办理人角色不在白名单: " + normalized + " (" + ownerId + ")", 400);
            }
            return;
        }
        if (!ALLOWED_SIMPLE_RULES.contains(normalized)) {
            throw new ServiceException("办理人规则不在白名单: " + normalized + " (" + ownerId + ")", 400);
        }
    }

    // ------------------------------------------------------------------ 发射

    /** 顺序发射一组节点；{@code entryRef} 是入口元素，返回值是出口端口 */
    private static Port emitSequence(List<FlowChainNode> nodes, int startX, int centerY, int depth, Ctx ctx,
                                     String entryRef, String entryCondition, Integer entryMidY) {
        String currentRef = entryRef;
        String pendingCondition = entryCondition;
        Integer pendingMidY = entryMidY;
        int x = startX;
        if (nodes == null) {
            return new Port(x, currentRef, x, centerY);
        }
        for (FlowChainNode node : nodes) {
            switch (node.getType()) {
                case FlowChainNode.TYPE_CC -> {
                    // 抄送不占 BPMN 图元、不消耗槽位
                }
                case FlowChainNode.TYPE_APPROVE -> {
                    ctx.elements.add(new Element(node.getId(), "userTask", node.getName(), x, centerY, TASK_W, TASK_H));
                    ctx.edges.add(new Edge("flow_" + currentRef + "_" + node.getId(), currentRef, node.getId(),
                        pendingCondition, pendingMidY));
                    currentRef = node.getId();
                    pendingCondition = null;
                    pendingMidY = null;
                    x += H_STEP;
                }
                case FlowChainNode.TYPE_BRANCH -> {
                    Port branchPort = emitBranch(node, x, centerY, depth, ctx, currentRef, pendingCondition, pendingMidY);
                    currentRef = branchPort.ref();
                    pendingCondition = null;
                    pendingMidY = null;
                    x = branchPort.nextX();
                }
                default -> throw new ServiceException("未知审批链节点类型: " + node.getType(), 400);
            }
        }
        return new Port(x, currentRef, x, centerY);
    }

    /** 条件分支：拆分网关 → N 支 → 汇聚网关 */
    private static Port emitBranch(FlowChainNode node, int x, int centerY, int depth, Ctx ctx,
                                   String entryRef, String entryCondition, Integer entryMidY) {
        String splitId = node.getId() + "_split";
        String joinId = node.getId() + "_join";
        ctx.elements.add(new Element(splitId, "exclusiveGateway", node.getName(), x, centerY, GW_W, GW_H));
        ctx.edges.add(new Edge("flow_" + entryRef + "_" + splitId, entryRef, splitId, entryCondition, entryMidY));

        List<FlowChainNode.FlowBranch> branches = node.getBranches();
        int n = branches.size();
        int maxSlotX = x + H_STEP;
        for (int i = 0; i < n; i++) {
            FlowChainNode.FlowBranch branch = branches.get(i);
            int rowY = centerY + (2 * i - (n - 1)) * V_STEP / 2;
            String condition = branch.getCondition() == null || branch.getCondition().isDefaultElse()
                ? null : conditionExpression(branch.getCondition());
            List<FlowChainNode> inner = branch.getNodes() == null ? List.of() : branch.getNodes();
            if (inner.isEmpty()) {
                ctx.edges.add(new Edge("flow_" + splitId + "_" + joinId + "_" + i, splitId, joinId, condition, rowY));
                continue;
            }
            Port exit = emitSequence(inner, x + H_STEP, rowY, depth + 1, ctx, splitId, condition, rowY);
            if (!joinId.equals(exit.ref())) {
                ctx.edges.add(new Edge("flow_" + exit.ref() + "_" + joinId + "_" + i, exit.ref(), joinId, null, rowY));
            }
            maxSlotX = Math.max(maxSlotX, exit.nextX());
        }

        ctx.elements.add(new Element(joinId, "exclusiveGateway", node.getName() + "汇聚", maxSlotX, centerY, GW_W, GW_H));
        return new Port(maxSlotX + H_STEP, joinId, maxSlotX, centerY);
    }

    /** 白名单条件表达式：${amount_gt_50000} */
    static String conditionExpression(FlowCondition condition) {
        return "${" + conditionVariable(condition.getField(), condition.getOp(), condition.getValue().longValue()) + "}";
    }

    /** 条件变量名：amount_gt_50000 */
    public static String conditionVariable(String field, String op, long value) {
        return field + "_" + op + "_" + value;
    }

    /** 从 BPMN 条件表达式反解条件变量名（运行时求值用） */
    public static String variableOfExpression(String expression) {
        if (expression == null) {
            return null;
        }
        String text = expression.trim();
        if (text.startsWith("${") && text.endsWith("}")) {
            text = text.substring(2, text.length() - 1).trim();
        }
        if (text.startsWith("!")) {
            text = text.substring(1).trim();
        }
        return text;
    }

    // ------------------------------------------------------------------ 渲染

    private static String render(String processKey, String processName, Ctx ctx) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n");
        sb.append("             xmlns:flowable=\"http://flowable.org/bpmn\"\n");
        sb.append("             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("             xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\"\n");
        sb.append("             xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\"\n");
        sb.append("             xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\"\n");
        sb.append("             targetNamespace=\"http://agentoa.org/workflow\">\n");
        sb.append("  <process id=\"").append(escape(processKey)).append("\" name=\"").append(escape(processName))
            .append("\" isExecutable=\"true\">\n");

        for (Element element : ctx.elements) {
            switch (element.kind) {
                case "startEvent" -> sb.append("    <startEvent id=\"").append(element.id)
                    .append("\" name=\"").append(escape(element.name)).append("\"/>\n");
                case "endEvent" -> sb.append("    <endEvent id=\"").append(element.id)
                    .append("\" name=\"").append(escape(element.name)).append("\"/>\n");
                case "exclusiveGateway" -> sb.append("    <exclusiveGateway id=\"").append(element.id)
                    .append("\" name=\"").append(escape(element.name)).append("\"/>\n");
                case "userTask" -> renderUserTask(sb, element, ctx);
                default -> throw new IllegalStateException("未知元素类型: " + element.kind);
            }
        }
        for (Edge edge : ctx.edges) {
            sb.append("    <sequenceFlow id=\"").append(edge.id).append("\" sourceRef=\"").append(edge.source)
                .append("\" targetRef=\"").append(edge.target).append("\"");
            if (edge.condition == null) {
                sb.append("/>\n");
            } else {
                sb.append(">\n");
                sb.append("      <conditionExpression xsi:type=\"tFormalExpression\">")
                    .append(escape(edge.condition)).append("</conditionExpression>\n");
                sb.append("    </sequenceFlow>\n");
            }
        }
        sb.append("  </process>\n");
        renderDiagram(sb, ctx);
        sb.append("</definitions>\n");
        return sb.toString();
    }

    private static void renderUserTask(StringBuilder sb, Element element, Ctx ctx) {
        FlowChainNode node = ctx.nodeOf(element.id);
        FlowSignMode mode = FlowSignMode.from(node.getSignMode());
        sb.append("    <userTask id=\"").append(element.id).append("\" name=\"").append(escape(element.name)).append("\"");
        if (mode != FlowSignMode.SINGLE) {
            sb.append(" flowable:collection=\"${mi_").append(element.id).append("}\"")
                .append(" flowable:elementVariable=\"assignee\"");
        }
        sb.append(">\n");
        sb.append("      <extensionElements>\n");
        sb.append("        <flowable:taskListener event=\"create\" class=\"org.dromara.agentoa.workflow.assigner.AssigneeTaskListener\">\n");
        sb.append("          <flowable:field name=\"rule\"><flowable:string>")
            .append(escape(node.getAssigneeRule().trim())).append("</flowable:string></flowable:field>\n");
        sb.append("        </flowable:taskListener>\n");
        sb.append("      </extensionElements>\n");
        if (mode != FlowSignMode.SINGLE) {
            sb.append("      <multiInstanceLoopCharacteristics isSequential=\"false\"")
                .append(" flowable:collection=\"${mi_").append(element.id).append("}\"")
                .append(" flowable:elementVariable=\"assignee\">\n");
            sb.append("        <completionCondition>").append(
                mode == FlowSignMode.COUNTERSIGN
                    ? "${nrOfCompletedInstances == nrOfInstances}"
                    : "${nrOfCompletedInstances >= 1}")
                .append("</completionCondition>\n");
            sb.append("      </multiInstanceLoopCharacteristics>\n");
        }
        sb.append("    </userTask>\n");
    }

    private static void renderDiagram(StringBuilder sb, Ctx ctx) {
        sb.append("  <bpmndi:BPMNDiagram id=\"Diagram_").append(escape(ctx.processKey)).append("\">\n");
        sb.append("    <bpmndi:BPMNPlane bpmnElement=\"").append(escape(ctx.processKey)).append("\" id=\"Plane_")
            .append(escape(ctx.processKey)).append("\">\n");
        for (Element element : ctx.elements) {
            sb.append("      <bpmndi:BPMNShape bpmnElement=\"").append(element.id).append("\" id=\"Shape_")
                .append(element.id).append("\">\n");
            sb.append("        <dc:Bounds height=\"").append(fmt(element.h)).append("\" width=\"")
                .append(fmt(element.w)).append("\" x=\"").append(fmt(element.x - element.w / 2))
                .append("\" y=\"").append(fmt(element.y - element.h / 2)).append("\"/>\n");
            sb.append("      </bpmndi:BPMNShape>\n");
        }
        for (Edge edge : ctx.edges) {
            Element source = ctx.elementOf(edge.source);
            Element target = ctx.elementOf(edge.target);
            if (source == null || target == null) {
                continue;
            }
            double sx = source.x + source.w / 2;
            double sy = source.y;
            double tx = target.x - target.w / 2;
            double ty = target.y;
            sb.append("      <bpmndi:BPMNEdge bpmnElement=\"").append(edge.id).append("\" id=\"Edge_")
                .append(edge.id).append("\">\n");
            sb.append("        <di:waypoint x=\"").append(fmt(sx)).append("\" y=\"").append(fmt(sy)).append("\"/>\n");
            if (edge.midY != null && Math.abs(edge.midY - ty) > 1) {
                sb.append("        <di:waypoint x=\"").append(fmt((sx + tx) / 2)).append("\" y=\"")
                    .append(fmt(edge.midY)).append("\"/>\n");
            }
            sb.append("        <di:waypoint x=\"").append(fmt(tx)).append("\" y=\"").append(fmt(ty)).append("\"/>\n");
            sb.append("      </bpmndi:BPMNEdge>\n");
        }
        sb.append("    </bpmndi:BPMNPlane>\n");
        sb.append("  </bpmndi:BPMNDiagram>\n");
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? (long) value + ".0" : String.valueOf(value);
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 上下文

    private static final class Element {
        final String id;
        final String kind;
        final String name;
        final double x;
        final double y;
        final double w;
        final double h;

        Element(String id, String kind, String name, double x, double y, double w, double h) {
            this.id = id;
            this.kind = kind;
            this.name = name;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static final class Edge {
        final String id;
        final String source;
        final String target;
        final String condition;
        final Integer midY;

        Edge(String id, String source, String target, String condition, Integer midY) {
            this.id = id;
            this.source = source;
            this.target = target;
            this.condition = condition;
            this.midY = midY;
        }
    }

    private static final class Ctx {
        String processKey;
        Predicate<String> roleExists;
        final Set<String> ids = new HashSet<>();
        final List<Element> elements = new ArrayList<>();
        final List<Edge> edges = new ArrayList<>();
        final List<CcRule> ccRules = new ArrayList<>();
        final List<String> rules = new ArrayList<>();
        final List<FlowChainNode> userTasks = new ArrayList<>();
        int taskCount;
        int gatewayCount;

        FlowChainNode nodeOf(String id) {
            for (FlowChainNode node : userTasks) {
                if (node.getId().equals(id)) {
                    return node;
                }
            }
            throw new IllegalStateException("未登记的 userTask: " + id);
        }

        Element elementOf(String id) {
            for (Element element : elements) {
                if (element.id.equals(id)) {
                    return element;
                }
            }
            return null;
        }
    }
}

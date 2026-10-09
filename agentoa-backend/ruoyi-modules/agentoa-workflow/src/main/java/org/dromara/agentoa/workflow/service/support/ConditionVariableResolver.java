package org.dromara.agentoa.workflow.service.support;

import org.dromara.common.core.exception.ServiceException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 条件分支变量求值（受控）：从 BPMN 条件表达式反解出 {@code amount_<op>_<阈值>} 变量，
 * 用业务金额一次性求出布尔值注入流程变量。
 * <p>
 * 只认白名单变量形态，不解析任何表达式/SpEL；缺失金额且变量未预置时直接 400，
 * 避免"条件永远走 else"的静默误路由。
 */
public final class ConditionVariableResolver {

    /** 条件变量形态：amount_gt_50000 / amount_le_1000 … */
    private static final Pattern CONDITION_VARIABLE = Pattern.compile(
        "amount_(gt|ge|lt|le|eq|ne)_(\\d{1,10})");

    /** 业务金额变量名 */
    public static final String AMOUNT_VARIABLE = "amount";

    private ConditionVariableResolver() {
    }

    /**
     * 收集 BPMN 中出现的全部条件变量名。
     */
    public static Set<String> collectVariables(String bpmnXml) {
        Set<String> variables = new TreeSet<>();
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return variables;
        }
        Matcher matcher = Pattern.compile(
            "<conditionExpression[^>]*>\\s*\\$\\{(!?)(amount_(?:gt|ge|lt|le|eq|ne)_\\d{1,10})}\\s*</conditionExpression>")
            .matcher(bpmnXml);
        while (matcher.find()) {
            variables.add(matcher.group(2));
        }
        return variables;
    }

    /**
     * 就地补齐条件变量。已被业务承接器显式赋值的变量保持不变（兼容 reimburse 等既有实现）。
     *
     * @param variables 流程变量（会被修改）
     * @param bpmnXml   受控 BPMN
     */
    public static void enrich(Map<String, Object> variables, String bpmnXml) {
        Set<String> required = collectVariables(bpmnXml);
        if (required.isEmpty()) {
            return;
        }
        BigDecimal amount = null;
        for (String name : required) {
            if (variables.containsKey(name)) {
                continue;
            }
            if (amount == null) {
                amount = requireAmount(variables);
            }
            variables.put(name, evaluate(amount, name));
        }
    }

    private static BigDecimal requireAmount(Map<String, Object> variables) {
        Object raw = variables.get(AMOUNT_VARIABLE);
        if (raw == null) {
            throw new ServiceException("流程包含金额条件分支，但未提供金额变量 amount", 400);
        }
        try {
            return raw instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("金额变量 amount 不是合法数字: " + raw, 400);
        }
    }

    /** 求单个条件变量的布尔值 */
    public static boolean evaluate(BigDecimal amount, String variable) {
        Matcher matcher = CONDITION_VARIABLE.matcher(variable);
        if (!matcher.matches()) {
            throw new ServiceException("条件变量形态非法: " + variable, 400);
        }
        String op = matcher.group(1);
        BigDecimal threshold = new BigDecimal(matcher.group(2));
        int cmp = amount.compareTo(threshold);
        return switch (op) {
            case "gt" -> cmp > 0;
            case "ge" -> cmp >= 0;
            case "lt" -> cmp < 0;
            case "le" -> cmp <= 0;
            case "eq" -> cmp == 0;
            case "ne" -> cmp != 0;
            default -> throw new ServiceException("条件算子非法: " + op, 400);
        };
    }
}

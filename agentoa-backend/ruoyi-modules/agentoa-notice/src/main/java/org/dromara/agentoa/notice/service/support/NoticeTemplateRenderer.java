package org.dromara.agentoa.notice.service.support;

import org.dromara.common.core.exception.ServiceException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通知模板渲染（NC-04）：受控 {key} 占位符白名单替换，不引入 FreeMarker/SpEL/脚本（对齐 M0 禁表达式原则）。
 * <p>
 * 规则：占位符必须全部在变量声明内；渲染时占位符必须有值，未声明的传入变量拒绝。
 */
public final class NoticeTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)}");
    private static final Pattern VAR_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private NoticeTemplateRenderer() {
    }

    /** 提取模板中的占位符集合 */
    public static Set<String> placeholders(String text) {
        Set<String> names = new LinkedHashSet<>();
        if (text == null) {
            return names;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /** 校验变量声明合法且模板占位符全部已声明 */
    public static Set<String> checkDeclared(String titleTpl, String contentTpl, Collection<String> declaredVars) {
        Set<String> declared = new LinkedHashSet<>();
        if (declaredVars != null) {
            for (String name : declaredVars) {
                if (name == null || name.isBlank()) {
                    throw new ServiceException("NT_TPL_VAR_INVALID 变量名不能为空", 400);
                }
                String trimmed = name.trim();
                if (!VAR_NAME.matcher(trimmed).matches()) {
                    throw new ServiceException("NT_TPL_VAR_INVALID 变量名非法: " + trimmed, 400);
                }
                if (!declared.add(trimmed)) {
                    throw new ServiceException("NT_TPL_VAR_INVALID 变量名重复: " + trimmed, 400);
                }
            }
        }
        if (declared.size() > 20) {
            throw new ServiceException("NT_TPL_VAR_INVALID 变量数不能超过20", 400);
        }
        Set<String> used = new LinkedHashSet<>();
        used.addAll(placeholders(titleTpl));
        used.addAll(placeholders(contentTpl));
        for (String name : used) {
            if (!declared.contains(name)) {
                throw new ServiceException("NT_TPL_UNDECLARED_VAR 模板变量未声明: " + name, 400);
            }
        }
        return declared;
    }

    /** 渲染单个模板：缺失占位符取值拒绝、未声明变量拒绝 */
    public static String render(String template, Collection<String> declaredVars, Map<String, String> vars) {
        Set<String> declared = declaredVars == null ? Set.of() : new LinkedHashSet<>(declaredVars);
        Map<String, String> values = vars == null ? Map.of() : vars;
        for (String key : values.keySet()) {
            if (!declared.contains(key)) {
                throw new ServiceException("NT_TPL_UNDECLARED_VAR 未声明的模板变量: " + key, 400);
            }
        }
        Set<String> used = placeholders(template);
        StringBuilder out = new StringBuilder();
        Matcher matcher = PLACEHOLDER.matcher(template == null ? "" : template);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!used.contains(name)) {
                continue;
            }
            String value = values.get(name);
            if (value == null) {
                throw new ServiceException("NT_TPL_VAR_MISSING 缺少模板变量取值: " + name, 400);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}

package org.dromara.agentoa.notice;

import org.dromara.agentoa.notice.service.support.NoticeTemplateRenderer;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 通知模板渲染（NC-04）：{key} 白名单替换、未声明/缺变量拒绝（M0 禁表达式原则）。
 */
class NoticeTemplateRendererTest {

    @Test
    void placeholdersExtractedFromTemplate() {
        assertThat(NoticeTemplateRenderer.placeholders("Hi {name}，{dept}/{name}")).containsExactly("name", "dept");
    }

    @Test
    void declaredCheckRejectsUndeclaredPlaceholder() {
        assertThatThrownBy(() -> NoticeTemplateRenderer.checkDeclared("Hi {name}", "ok", List.of("dept")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_TPL_UNDECLARED_VAR");
    }

    @Test
    void declaredCheckRejectsIllegalOrDuplicateNames() {
        assertThatThrownBy(() -> NoticeTemplateRenderer.checkDeclared("ok", "ok", List.of("a-b")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_TPL_VAR_INVALID");
        assertThatThrownBy(() -> NoticeTemplateRenderer.checkDeclared("ok", "ok", List.of("x", "x")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_TPL_VAR_INVALID");
    }

    @Test
    void renderSubstitutesWhitelistedPlaceholders() {
        String rendered = NoticeTemplateRenderer.render("欢迎 {name} 加入 {dept}",
            Set.of("name", "dept"), Map.of("name", "张三", "dept", "研发"));
        assertThat(rendered).isEqualTo("欢迎 张三 加入 研发");
    }

    @Test
    void renderRejectsMissingValueAndUnknownVariable() {
        assertThatThrownBy(() -> NoticeTemplateRenderer.render("Hi {name}", Set.of("name"), Map.of()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_TPL_VAR_MISSING");
        assertThatThrownBy(() -> NoticeTemplateRenderer.render("Hi {name}", Set.of("name"),
            Map.of("name", "x", "extra", "y")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("NT_TPL_UNDECLARED_VAR");
    }

    @Test
    void plainBracesAreLiterals() {
        assertThat(NoticeTemplateRenderer.render("金额 {a+b} 共 {n} 元", Set.of("n"), Map.of("n", "3")))
            .isEqualTo("金额 {a+b} 共 3 元");
    }
}

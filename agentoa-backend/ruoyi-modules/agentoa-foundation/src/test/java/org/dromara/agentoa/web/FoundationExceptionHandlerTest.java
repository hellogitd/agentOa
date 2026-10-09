package org.dromara.agentoa.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.dromara.common.core.domain.R;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 信封错误收口契约（docs/05 §1.5）：校验失败必须回显字段级原因（「参数校验失败：…」），
 * 缺少 Idempotency-Key 等请求头为 400 可读提示，不得落 500。
 */
class FoundationExceptionHandlerTest {

    /** 取一个真实 {@link MethodParameter}，避免依赖异常构造器对 mock 参数的取值行为 */
    @SuppressWarnings("unused")
    static class DummyBo {
        void create(String definitionId) {
        }
    }

    private static MethodParameter parameter() throws NoSuchMethodException {
        return new MethodParameter(DummyBo.class.getDeclaredMethod("create", String.class), 0);
    }

    private final FoundationExceptionHandler handler = new FoundationExceptionHandler();

    @Test
    void methodArgumentNotValidSurfacesFieldMessage() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "genericRequestBo");
        binding.addError(new FieldError("genericRequestBo", "definitionId", "definitionId 不能为空"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter(), binding);

        R<Void> response = handler.invalidInput(ex);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMsg()).isEqualTo("参数校验失败：definitionId 不能为空");
    }

    @Test
    @SuppressWarnings("unchecked")
    void constraintViolationSurfacesMessage() throws Exception {
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("标题长度不能超过255个字符");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        R<Void> response = handler.invalidInput(ex);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMsg()).isEqualTo("参数校验失败：标题长度不能超过255个字符");
    }

    @Test
    void missingIdempotencyHeaderIs400WithReadableMessage() throws Exception {
        MissingRequestHeaderException ex = new MissingRequestHeaderException("Idempotency-Key", parameter());

        R<Void> response = handler.missingHeader(ex);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMsg()).isEqualTo("缺少必要请求头：Idempotency-Key");
    }

    @Test
    void unreadableBodyKeepsCauseReadable() {
        R<Void> response = handler.unreadableBody(
            new HttpMessageNotReadableException("JSON 解析失败", mock(HttpInputMessage.class)));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMsg()).startsWith("请求参数格式错误：");
    }
}

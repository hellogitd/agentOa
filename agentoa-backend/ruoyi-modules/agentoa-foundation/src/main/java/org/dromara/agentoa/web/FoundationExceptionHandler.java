package org.dromara.agentoa.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.user.UserException;
import org.dromara.common.core.utils.StreamUtils;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 信封错误收口（docs/05 §1.5）：校验失败的 msg 必须携带具体原因（如「参数校验失败：手机号格式不正确」），
 * 不做通用文案吞并；缺少请求头/参数、类型不匹配统一 400，不落 500。
 */
@Order(-100)
@RestControllerAdvice
public class FoundationExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        HandlerMethodValidationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> invalidInput(Exception e) {
        return R.fail(400, "参数校验失败：" + validationMessages(e));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> unreadableBody(HttpMessageNotReadableException e) {
        return R.fail(400, "请求参数格式错误：" + e.getMostSpecificCause().getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> missingHeader(MissingRequestHeaderException e) {
        return R.fail(400, "缺少必要请求头：" + e.getHeaderName());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
        MissingPathVariableException.class,
        MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> invalidParameter(Exception e) {
        String detail;
        if (e instanceof MissingServletRequestParameterException missing) {
            detail = "缺少必要参数：" + missing.getParameterName();
        } else if (e instanceof MissingPathVariableException missing) {
            detail = "缺少路径参数：" + missing.getVariableName();
        } else {
            detail = "参数类型不正确：" + ((MethodArgumentTypeMismatchException) e).getName();
        }
        return R.fail(400, detail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public R<Void> tooLarge(MaxUploadSizeExceededException e) {
        return R.fail(413, "文件超过上传限制");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<R<Void>> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(R.fail(e.getStatusCode().value(), e.getReason()));
    }

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> authentication(NotLoginException e) {
        return R.fail(401, "登录已过期，请重新登录");
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> permission(Exception e) {
        return R.fail(403, "没有访问权限");
    }

    @ExceptionHandler(UserException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> invalidCredentials(UserException e) {
        return R.fail(401, "登录失败，请检查凭据或验证码");
    }

    private static String validationMessages(Exception e) {
        String messages;
        if (e instanceof MethodArgumentNotValidException notValid) {
            messages = StreamUtils.join(notValid.getBindingResult().getAllErrors(),
                DefaultMessageSourceResolvable::getDefaultMessage, ", ");
        } else if (e instanceof ConstraintViolationException violation) {
            messages = StreamUtils.join(violation.getConstraintViolations(), ConstraintViolation::getMessage, ", ");
        } else {
            messages = StreamUtils.join(((HandlerMethodValidationException) e).getAllErrors(),
                MessageSourceResolvable::getDefaultMessage, ", ");
        }
        return messages == null || messages.isBlank() ? "请求参数不符合要求" : messages;
    }
}

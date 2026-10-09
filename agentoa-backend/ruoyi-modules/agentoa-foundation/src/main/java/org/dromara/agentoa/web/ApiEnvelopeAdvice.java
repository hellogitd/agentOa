package org.dromara.agentoa.web;

import org.dromara.common.core.domain.R;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class ApiEnvelopeAdvice implements ResponseBodyAdvice<Object> {
    @Override public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object beforeBodyWrite(Object body, MethodParameter type, MediaType media,
        Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (!request.getURI().getPath().startsWith("/api/v1/") || !(body instanceof R<?> result)) { return body; }
        int code = result.getCode();
        if (code >= 400 && code <= 599) { response.setStatusCode(org.springframework.http.HttpStatusCode.valueOf(code)); }
        var output = new LinkedHashMap<String,Object>();
        output.put("code", code); output.put("msg", result.getMsg()); output.put("data", result.getData());
        output.put("timestamp", System.currentTimeMillis()); output.put("requestId", MDC.get("requestId"));
        return output;
    }
}

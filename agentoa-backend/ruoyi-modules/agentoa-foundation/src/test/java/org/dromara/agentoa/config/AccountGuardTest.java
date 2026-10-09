package org.dromara.agentoa.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.core.service.PermissionService;
import org.dromara.system.service.ISysRoleService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AccountGuard 契约回归（docs/21 §9.2 契约回归暴露项）：
 * SSE 完成路径触发的 ASYNC 分派必须短路返回，禁止鉴权链在 emitter.complete() 后重跑
 * （否则全局异常器会把 JSON 信封写进事件流尾部）。
 */
class AccountGuardTest {

    /** 暴露 {@link InterceptorRegistry#getInterceptors()}（protected）用于取注册的拦截器 */
    private static final class ExposedRegistry extends InterceptorRegistry {
        List<Object> exposed() {
            return getInterceptors();
        }
    }

    @Test
    void asyncDispatchShortCircuitsWithoutTouchingAuthChain() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AccountGuard guard = new AccountGuard(jdbc, mock(PermissionService.class), mock(ISysRoleService.class));
        ExposedRegistry registry = new ExposedRegistry();
        guard.addInterceptors(registry);
        List<Object> interceptors = registry.exposed();
        assertThat(interceptors).hasSize(1);
        HandlerInterceptor interceptor = ((MappedInterceptor) interceptors.get(0)).getInterceptor();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getDispatcherType()).thenReturn(DispatcherType.ASYNC);
        boolean proceed = interceptor.preHandle(request, mock(HttpServletResponse.class), new Object());

        assertThat(proceed).isFalse();
        verifyNoInteractions(jdbc);
    }
}

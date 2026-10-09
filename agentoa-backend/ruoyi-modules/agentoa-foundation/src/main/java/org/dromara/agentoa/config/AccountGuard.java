package org.dromara.agentoa.config;

import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Set;

@Configuration @RequiredArgsConstructor
public class AccountGuard implements WebMvcConfigurer {
    private final JdbcTemplate jdbc;
    private final org.dromara.common.core.service.PermissionService permissions;
    private final org.dromara.system.service.ISysRoleService roles;
    private static final Set<String> PASSWORD_PATHS = Set.of("/api/v1/auth/profile", "/api/v1/auth/password", "/api/v1/auth/logout", "/auth/logout");
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (request.getDispatcherType() == DispatcherType.ASYNC) {
                    return false;
                }
                String path = request.getRequestURI();
                if (path.equals("/system/user/profile/updatePwd") || path.equals("/system/user/importData") || path.equals("/system/user/profile/avatar")) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Use the AgentOA account lifecycle APIs");
                }
                if (path.startsWith("/system/tenant") || path.startsWith("/resource/oss") || path.startsWith("/system/ossConfig")
                    || path.startsWith("/auth/register") || path.startsWith("/auth/binding") || path.startsWith("/auth/social")) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Capability is not enabled");
                }
                boolean loggedIn;
                try {
                    loggedIn = StpUtil.isLogin();
                } catch (SaTokenContextException e) {
                    return true;
                }
                if (!loggedIn) { return true; }
                LoginUser user = LoginHelper.getLoginUser();
                if(user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Session unavailable");
                var rows = jdbc.queryForList("SELECT status,del_flag,must_change_password,dept_id FROM sys_user WHERE user_id=?", user.getUserId());
                if (rows.isEmpty() || !"0".equals(rows.get(0).get("status")) || !"0".equals(rows.get(0).get("del_flag"))) {
                    StpUtil.logout(user.getLoginId());
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account disabled");
                }
                if (((Number) rows.get(0).get("must_change_password")).intValue() == 1 && !PASSWORD_PATHS.contains(path)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED");
                }
                // Refresh authorization on every request, including data-scope roles.
                user.setMenuPermission(permissions.getMenuPermission(user.getUserId()));
                user.setRolePermission(permissions.getRolePermission(user.getUserId()));
                user.setRoles(cn.hutool.core.bean.BeanUtil.copyToList(roles.selectRolesByUserId(user.getUserId()), org.dromara.common.core.domain.dto.RoleDTO.class));
                Object dept=rows.get(0).get("dept_id");
                user.setDeptId(dept == null ? null : ((Number)dept).longValue());
                StpUtil.getTokenSession().set(LoginHelper.LOGIN_USER_KEY,user);
                return true;
            }
        }).addPathPatterns("/**").excludePathPatterns("/actuator/**", "/error").order(-10);
    }
}

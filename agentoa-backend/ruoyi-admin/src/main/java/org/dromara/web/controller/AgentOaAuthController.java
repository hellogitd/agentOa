package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.service.ISysClientService;
import org.dromara.web.service.impl.PasswordAuthStrategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AgentOaAuthController {
    private final PasswordAuthStrategy passwords;
    private final ISysClientService clients;
    private final JdbcTemplate jdbc;

    @SaIgnore @PostMapping("/login")
    public R<Map<String,Object>> login(@RequestBody Map<String,String> input) {
        Map<String,String> body = new LinkedHashMap<>();
        body.put("username", input.get("username"));
        body.put("password", input.get("password"));
        body.put("code", input.getOrDefault("captchaCode", ""));
        body.put("uuid", input.getOrDefault("captchaUuid", ""));
        body.put("tenantId", "000000");
        body.put("clientId", "e5cd7e4891bf95d1d19206ce24a7b32e");
        body.put("grantType", "password");
        var client = clients.queryByClientId(body.get("clientId"));
        if (client == null || !"0".equals(client.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Client unavailable");
        }
        var login = passwords.login(JsonUtils.toJsonString(body), client);
        return R.ok(Map.of("accessToken", login.getAccessToken(), "expiresIn", login.getExpireIn(),
            "tokenType", "Bearer", "user", profileData()));
    }

    @GetMapping("/profile") public R<Map<String,Object>> profile() { return R.ok(profileData()); }
    private Map<String,Object> profileData() {
        LoginUser user = LoginHelper.getLoginUser();
        return Map.of("userId", user.getUserId().toString(), "username", user.getUsername(),
            "nickname", user.getNickname(), "roles", user.getRolePermission(), "permissions", user.getMenuPermission(),
            "mustChangePassword", jdbc.queryForObject("SELECT must_change_password FROM sys_user WHERE user_id=?", Integer.class, user.getUserId()) == 1);
    }

    @PostMapping("/logout") public R<Void> logout() { StpUtil.logout(); return R.ok(); }

    @org.springframework.transaction.annotation.Transactional
    @PutMapping("/password") public R<Void> password(@RequestBody Map<String,String> body) {
        Long id = LoginHelper.getUserId();
        String next = body.getOrDefault("newPassword", "");
        String old = body.getOrDefault("oldPassword", "");
        if (next.length() < 12 || next.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 || next.equals(old)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a different password of 12-72 UTF-8 bytes");
        }
        String hash = jdbc.queryForObject("SELECT password FROM sys_user WHERE user_id=?", String.class, id);
        if (!BCrypt.checkpw(old, hash)) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect"); }
        int updated = jdbc.update("UPDATE sys_user SET password=?,must_change_password=0,update_time=CURRENT_TIMESTAMP WHERE user_id=? AND password=?", BCrypt.hashpw(next), id, hash);
        if (updated != 1) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Password changed concurrently"); }
        // Record before invalidating the session; never serialize credential fields.
        jdbc.update("INSERT INTO sys_oper_log(oper_id,title,business_type,method,request_method,operator_type,oper_name,dept_name,oper_url,status,oper_time,cost_time) VALUES(?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?)",
            cn.hutool.core.util.IdUtil.getSnowflakeNextId(),"修改登录密码",2,"AgentOaAuthController.password","PUT",1,LoginHelper.getUsername(),LoginHelper.getDeptName(),"/api/v1/auth/password",0,0);
        StpUtil.logout(LoginHelper.getLoginUser().getLoginId());
        return R.ok();
    }
}

package org.dromara.agentoa.config;

import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** One-time administrator initialization; never resets an existing administrator. */
@Component
@RequiredArgsConstructor
public class BootstrapAdministrator implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    @Value("${agentoa.bootstrap-password:}") private String password;

    @Override public void run(ApplicationArguments args) {
        transactions.executeWithoutResult(status -> {
            jdbc.queryForList("SELECT user_id FROM sys_user WHERE user_id=1 FOR UPDATE");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM oa_bootstrap WHERE id=1", Integer.class) > 0) { return; }
            if (password.length() < 16 || password.length() > 72) {
                throw new IllegalStateException("Initial administrator password must contain 16-72 characters; configure BOOTSTRAP_PASSWORD or a secret file");
            }
            jdbc.update("UPDATE sys_user SET password=?,status='0',must_change_password=1 WHERE user_id=1", BCrypt.hashpw(password));
            jdbc.update("INSERT INTO oa_bootstrap(id) VALUES(1)");
        });
    }
}

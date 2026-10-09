package org.dromara.agentoa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableScheduling
public class FoundationConfiguration {
    @Bean public TransactionTemplate foundationTransactions(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }
}

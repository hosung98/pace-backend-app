package com.pace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class TransactionConfig {
    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }
}

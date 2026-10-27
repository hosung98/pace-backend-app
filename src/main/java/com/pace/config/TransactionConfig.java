package com.pace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 명시적 트랜잭션 제어가 필요한 서비스에서 사용할 TransactionTemplate을 제공합니다. */
@Configuration
public class TransactionConfig {
    /** 공통 PlatformTransactionManager를 래핑한 템플릿을 생성합니다. */
    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }
}

package com.pace.common.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ApiLoggingAspect {
    private static final Logger log = LogManager.getLogger(ApiLoggingAspect.class);

    @Around("within(com.pace..controller..*)")
    public Object logDuration(ProceedingJoinPoint point) throws Throwable {
        log.debug("API 진입: handler={}", point.getSignature().toShortString());
        long started = System.nanoTime();
        try {
            return point.proceed();
        } finally {
            log.info("API 처리: handler={}, durationMs={}", point.getSignature().toShortString(),
                    (System.nanoTime() - started) / 1_000_000);
        }
    }
}

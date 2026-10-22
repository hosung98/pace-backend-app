package com.pace.common.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ApiLoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(ApiLoggingAspect.class);

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object logDuration(ProceedingJoinPoint point) throws Throwable {
        long started = System.nanoTime();
        try {
            return point.proceed();
        } finally {
            // 토큰·프로필·GPS가 남지 않도록 요청 인자와 응답 본문은 기록하지 않습니다.
            log.info("API 처리: handler={}, durationMs={}", point.getSignature().toShortString(),
                    (System.nanoTime() - started) / 1_000_000);
        }
    }
}

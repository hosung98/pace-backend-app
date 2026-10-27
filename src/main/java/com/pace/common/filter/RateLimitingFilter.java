package com.pace.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pace.common.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** IP 기반 고정 윈도우 제한으로 비정상 호출 폭주를 차단합니다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitingFilter extends OncePerRequestFilter {
    private static final long CLEANUP_INTERVAL = 1024L;
    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String REAL_IP = "X-Real-IP";

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();
    private final ObjectMapper json;
    private final boolean enabled;
    private final int maxRequests;
    private final int windowSeconds;

    public RateLimitingFilter(
            ObjectMapper json,
            @Value("${pace.rate-limit.enabled:true}") boolean enabled,
            @Value("${pace.rate-limit.max-requests:5000}") int maxRequests,
            @Value("${pace.rate-limit.window-seconds:60}") int windowSeconds) {
        this.json = json;
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) return true;
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator/health")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = Instant.now().getEpochSecond();
        String key = clientIp(request) + ":" + request.getMethod() + ":" + request.getRequestURI();
        Counter counter = counters.computeIfAbsent(key, ignored -> new Counter(now));

        int used;
        long resetAt;
        synchronized (counter) {
            if (now - counter.windowStartedAt >= windowSeconds) {
                counter.windowStartedAt = now;
                counter.used.set(0);
            }
            used = counter.used.incrementAndGet();
            resetAt = counter.windowStartedAt + windowSeconds;
        }

        if (used > maxRequests) {
            long retryAfter = Math.max(1L, resetAt - now);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            String requestId = String.valueOf(request.getAttribute("requestId"));
            ApiError body = new ApiError("TOO_MANY_REQUESTS",
                    "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", requestId, java.util.List.of());
            response.getWriter().write(json.writeValueAsString(body));
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, maxRequests - used)));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetAt));
        if (requests.incrementAndGet() % CLEANUP_INTERVAL == 0) cleanup(now);
        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma >= 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String real = request.getHeader(REAL_IP);
        if (real != null && !real.isBlank()) return real.trim();
        return request.getRemoteAddr();
    }

    private void cleanup(long now) {
        long staleBefore = now - (windowSeconds * 2L);
        for (Map.Entry<String, Counter> entry : counters.entrySet()) {
            Counter counter = entry.getValue();
            if (counter.windowStartedAt < staleBefore) counters.remove(entry.getKey(), counter);
        }
    }

    private static final class Counter {
        volatile long windowStartedAt;
        final AtomicInteger used = new AtomicInteger();

        Counter(long startedAt) {
            this.windowStartedAt = startedAt;
        }
    }
}

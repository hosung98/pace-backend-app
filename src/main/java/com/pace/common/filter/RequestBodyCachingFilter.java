package com.pace.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 요청 본문 재사용과 안전한 본문 디버그 로깅을 담당합니다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestBodyCachingFilter extends OncePerRequestFilter {
    private static final Logger log = LogManager.getLogger(RequestBodyCachingFilter.class);
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)\"(password|secret|token|idToken|refreshToken|authorizationCode|appleAuthorizationCode|clientSecret)\"\\s*:\\s*\"[^\"]*\"");
    private static final int UNLIMITED = -1;

    private final boolean enabled;
    private final int maxPayloadBytes;
    private final int maxLogChars;

    public RequestBodyCachingFilter(
            @Value("${pace.request-body-cache.enabled:true}") boolean enabled,
            @Value("${pace.request-body-cache.max-payload-bytes:1048576}") int maxPayloadBytes,
            @Value("${pace.request-body-cache.max-log-chars:2048}") int maxLogChars) {
        this.enabled = enabled;
        this.maxPayloadBytes = maxPayloadBytes;
        this.maxLogChars = maxLogChars;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) return true;
        String method = request.getMethod();
        if (!("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method))) {
            return true;
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) return true;
        long contentLength = request.getContentLengthLong();
        return maxPayloadBytes != UNLIMITED && contentLength > maxPayloadBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpServletRequest wrapped = request instanceof CachedBodyHttpServletRequest
                ? request : new CachedBodyHttpServletRequest(request);
        try {
            chain.doFilter(wrapped, response);
        } finally {
            if (log.isDebugEnabled() && wrapped instanceof CachedBodyHttpServletRequest cached) {
                String body = cached.getCachedBodyAsString();
                if (!body.isBlank()) {
                    String redacted = SENSITIVE.matcher(body).replaceAll("\"$1\":\"***\"");
                    String summarized = redacted.length() > maxLogChars
                            ? redacted.substring(0, maxLogChars) + "...(truncated)" : redacted;
                    log.debug("요청 본문: method={}, uri={}, body={}", request.getMethod(), request.getRequestURI(), summarized);
                }
            }
        }
    }
}

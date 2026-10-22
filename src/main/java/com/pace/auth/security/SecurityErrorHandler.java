package com.pace.auth.security;

import com.pace.common.exception.ApiError;
import com.pace.common.util.JsonCodec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 보안 필터의 인증·인가 실패를 공통 JSON 오류 응답으로 변환합니다. */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final JsonCodec json;

    public SecurityErrorHandler(JsonCodec json) {
        this.json = json;
    }

    /** 인증 실패는 WWW-Authenticate 헤더와 함께 401로 응답합니다. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        boolean expired = exception.getMessage() != null
                && exception.getMessage().toLowerCase(Locale.ROOT).contains("expired");
        response.setHeader("WWW-Authenticate", "Bearer");
        write(request, response, 401, expired ? "TOKEN_EXPIRED" : "UNAUTHORIZED",
                "유효한 접근 토큰이 필요합니다.");
    }

    /** 인증된 사용자의 권한 부족은 403으로 응답합니다. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(request, response, 403, "FORBIDDEN", "접근 권한이 없습니다.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json.write(ApiError.of(code, message, request)));
    }
}

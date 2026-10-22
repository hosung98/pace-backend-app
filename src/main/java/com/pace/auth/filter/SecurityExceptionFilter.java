package com.pace.auth.filter;

import com.pace.auth.security.SecurityErrorHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** MVC 밖에서 발생한 보안 예외만 처리하고 일반 서버 오류는 숨기지 않습니다. */
public class SecurityExceptionFilter extends OncePerRequestFilter {
    private final SecurityErrorHandler errors;

    public SecurityExceptionFilter(SecurityErrorHandler errors) {
        this.errors = errors;
    }

    /** 인증 필터를 감싸 미처리 인증·인가 예외를 일관된 응답으로 변환합니다. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            if (response.isCommitted()) throw ex;
            errors.commence(request, response, ex);
        } catch (AccessDeniedException ex) {
            if (response.isCommitted()) throw ex;
            errors.handle(request, response, ex);
        }
    }
}

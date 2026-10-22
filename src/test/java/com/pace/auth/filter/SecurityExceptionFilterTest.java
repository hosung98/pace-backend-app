package com.pace.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pace.auth.security.SecurityErrorHandler;
import com.pace.common.util.JsonCodec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

/** 필터 예외 처리와 인증 컨텍스트 정리·일반 예외 전파를 검증합니다. */
class SecurityExceptionFilterTest {
    private final SecurityExceptionFilter filter =
            new SecurityExceptionFilter(new SecurityErrorHandler(new JsonCodec(new ObjectMapper())));

    @Test
    void 인증_예외는_컨텍스트를_비우고_401로_변환한다() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.setAttribute("requestId", "filter-test");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user", "unused", java.util.List.of()));
        try {
            filter.doFilter(request, response, (req, res) -> { throw new BadCredentialsException("internal"); });
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
            assertThat(response.getContentAsString()).contains("UNAUTHORIZED", "filter-test").doesNotContain("internal");
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void 인가_예외는_403으로_변환하고_일반_예외는_전파한다() throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response,
                (req, res) -> { throw new AccessDeniedException("internal"); });
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("FORBIDDEN").doesNotContain("internal");
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> { throw new IllegalStateException("server failure"); }))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 커밋된_응답에_오류본문을_덧붙이지_않는다() throws Exception {
        var response = new MockHttpServletResponse();
        response.flushBuffer();
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), response,
                (req, res) -> { throw new BadCredentialsException("failure"); }))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(response.getContentAsString()).isEmpty();
    }
}

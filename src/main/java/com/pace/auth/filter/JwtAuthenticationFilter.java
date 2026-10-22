package com.pace.auth.filter;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

/** 토큰 추출과 SecurityContext 관리는 검증된 표준 필터 구현에 위임합니다. */
public class JwtAuthenticationFilter extends BearerTokenAuthenticationFilter {
    public JwtAuthenticationFilter(AuthenticationManager authenticationManager) {
        super(authenticationManager);
    }
}

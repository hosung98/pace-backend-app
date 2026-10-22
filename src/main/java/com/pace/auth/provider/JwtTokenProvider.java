package com.pace.auth.provider;

import com.pace.user.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** 표준 JWT 검증 후 활성 사용자 여부를 확인하는 인증 Provider입니다. */
public class JwtTokenProvider implements AuthenticationProvider {
    private final JwtAuthenticationProvider delegate;
    private final UserRepository users;

    public JwtTokenProvider(JwtDecoder decoder, UserRepository users) {
        this.delegate = new JwtAuthenticationProvider(decoder);
        this.users = users;
    }

    /** 서명·발급자·대상·만료 검증을 통과한 토큰만 사용자 조회에 사용합니다. */
    @Override
    public Authentication authenticate(Authentication authentication) {
        var authenticated = (JwtAuthenticationToken) delegate.authenticate(authentication);
        String subject = authenticated.getToken().getSubject();
        if (subject == null || subject.isBlank() || !users.existsByPublicIdAndStatus(subject, "active")) {
            throw new BadCredentialsException("유효하지 않은 토큰입니다.");
        }
        return authenticated;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}

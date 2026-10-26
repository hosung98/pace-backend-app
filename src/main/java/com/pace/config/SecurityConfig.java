package com.pace.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pace.auth.filter.JwtAuthenticationFilter;
import com.pace.auth.filter.SecurityExceptionFilter;
import com.pace.auth.provider.JwtTokenProvider;
import com.pace.auth.security.SecurityErrorHandler;
import com.pace.common.filter.AuthenticatedUserMdcFilter;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import com.pace.user.repository.UserRepository;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** 무상태 JWT 인증과 명시적인 공개 경로·CORS 정책을 구성합니다. */
@Configuration
public class SecurityConfig {
    /** 운영에서는 외부 주입 키만 허용하고 로컬에서만 임시 키를 생성합니다. */
    @Bean
    SecretKeySpec jwtKey(@Value("${pace.jwt-secret}") String secret, Environment environment) {
        byte[] bytes;
        if (secret.isBlank() && environment.acceptsProfiles(Profiles.of("local"))) {
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            LoggerFactory.getLogger(SecurityConfig.class)
                    .warn("로컬 전용 임시 JWT 키를 사용합니다. 서버 재시작 시 토큰이 무효화됩니다.");
        } else {
            try {
                bytes = Base64.getDecoder().decode(secret);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("JWT_SECRET은 Base64 형식이어야 합니다.", ex);
            }
            if (bytes.length < 32) {
                throw new IllegalStateException("JWT_SECRET은 최소 32바이트의 난수여야 합니다.");
            }
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKeySpec key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKeySpec key) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            return jwt.getSubject() != null && !jwt.getSubject().isBlank()
                    && jwt.getAudience().contains("pace-app") && jwt.getExpiresAt() != null
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "유효하지 않은 토큰입니다.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("pace-api"), requiredClaims));
        return decoder;
    }

    @Bean
    JwtTokenProvider jwtTokenProvider(JwtDecoder decoder, UserRepository users) {
        return new JwtTokenProvider(decoder, users);
    }

    /** 예외 필터 → JWT 인증 → 사용자 MDC → 인가 순서로 필터를 배치합니다. */
    @Bean
    SecurityFilterChain security(HttpSecurity http, JwtTokenProvider provider, SecurityErrorHandler errors,
                                 @Value("${springdoc.api-docs.enabled:false}") boolean docs) throws Exception {
        var jwtFilter = new JwtAuthenticationFilter(new ProviderManager(provider));
        jwtFilter.setAuthenticationEntryPoint(errors);
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/google", "/api/v1/auth/apple", "/api/v1/auth/refresh").permitAll()
                            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    if (docs) {
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .addFilterAt(jwtFilter, BearerTokenAuthenticationFilter.class)
                .addFilterBefore(new SecurityExceptionFilter(errors), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new AuthenticatedUserMdcFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource cors(@Value("${pace.cors-origins:}") String origins) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("X-Request-ID", "Location"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

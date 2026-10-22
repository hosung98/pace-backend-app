package com.pace.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pace.common.exception.GlobalExceptionHandler;
import com.pace.common.filter.AuthenticatedUserMdcFilter;
import com.pace.common.util.JsonCodec;
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

@Configuration
public class SecurityConfig {
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
    JwtDecoder jwtDecoder(SecretKeySpec key, UserRepository users) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> activeUser = jwt -> {
            boolean active = jwt.getSubject() != null && users.existsByPublicIdAndStatus(jwt.getSubject(), "active");
            return active && jwt.getAudience().contains("pace-app") && jwt.getExpiresAt() != null
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "유효하지 않은 토큰입니다.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("pace-api"), activeUser));
        return decoder;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, JsonCodec json,
                                 @Value("${springdoc.api-docs.enabled:false}") boolean docs) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/google", "/api/v1/auth/refresh").permitAll()
                            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    if (docs) {
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.setHeader("WWW-Authenticate", "Bearer");
                            boolean expired = ex.getMessage() != null
                                    && ex.getMessage().toLowerCase(java.util.Locale.ROOT).contains("expired");
                            response.getWriter().write(json.write(GlobalExceptionHandler.body(
                                    expired ? "TOKEN_EXPIRED" : "UNAUTHORIZED",
                                    "유효한 접근 토큰이 필요합니다.", request)));
                        }))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.setHeader("WWW-Authenticate", "Bearer");
                            response.getWriter().write(json.write(GlobalExceptionHandler.body(
                                    "UNAUTHORIZED", "로그인이 필요합니다.", request)));
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(json.write(GlobalExceptionHandler.body(
                                    "FORBIDDEN", "접근 권한이 없습니다.", request)));
                        }))
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

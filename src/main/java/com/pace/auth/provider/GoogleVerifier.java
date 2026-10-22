package com.pace.auth.provider;

import com.pace.common.exception.BusinessException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class GoogleVerifier {
    private final NimbusJwtDecoder decoder;

    public GoogleVerifier(@Value("${pace.google-client-ids}") String clientIds) {
        Set<String> audiences = Arrays.stream(clientIds.split(",")).map(String::trim)
                .filter(id -> !id.isEmpty()).collect(Collectors.toSet());
        if (audiences.isEmpty()) {
            throw new IllegalStateException("GOOGLE_CLIENT_IDS에 Google OAuth 클라이언트 ID를 설정해 주세요.");
        }
        decoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .restOperations(new RestTemplateBuilder().connectTimeout(Duration.ofSeconds(3))
                        .readTimeout(Duration.ofSeconds(5)).build()).build();
        OAuth2TokenValidator<Jwt> googleClaims = jwt -> {
            String issuer = jwt.getClaimAsString("iss");
            String azp = jwt.getClaimAsString("azp");
            boolean valid = Set.of("accounts.google.com", "https://accounts.google.com").contains(issuer == null ? "" : issuer)
                    && jwt.getAudience().stream().anyMatch(audiences::contains)
                    && (azp == null || audiences.contains(azp))
                    && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                    && jwt.getExpiresAt() != null;
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "유효하지 않은 Google 토큰입니다.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), googleClaims));
    }

    public Identity verify(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            return new Identity(jwt.getSubject(), jwt.getClaimAsString("name"));
        } catch (JwtException ex) {
            throw BusinessException.unauthorized();
        }
    }

    public record Identity(String subject, String name) {}
}

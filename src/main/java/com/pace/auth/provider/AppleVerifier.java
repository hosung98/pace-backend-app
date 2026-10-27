package com.pace.auth.provider;

import com.pace.common.exception.BusinessException;
import com.pace.common.util.TokenHash;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

    @Component
public class AppleVerifier {
    private final NimbusJwtDecoder decoder;

    @Autowired
    public AppleVerifier(@Value("${pace.apple-client-ids}") String clientIds) {
        this(clientIds, "https://appleid.apple.com/auth/keys");
    }

    AppleVerifier(String clientIds, String jwkSetUri) {
        Set<String> audiences = Arrays.stream(clientIds.split(",")).map(String::trim)
                .filter(id -> !id.isEmpty()).collect(Collectors.toSet());
        if (audiences.isEmpty()) {
            throw new IllegalStateException("APPLE_CLIENT_IDS에 Apple 클라이언트 ID를 설정해 주세요.");
        }
        decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplateBuilder().connectTimeout(Duration.ofSeconds(3))
                        .readTimeout(Duration.ofSeconds(5)).build()).build();
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            boolean valid = jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(audiences::contains)
                    && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                    && jwt.getSubject().length() <= 255 && jwt.getExpiresAt() != null
                    && jwt.getClaimAsString("nonce") != null;
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                            "유효하지 않은 Apple 토큰입니다.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("https://appleid.apple.com"), requiredClaims));
    }

    public Identity verify(String token, String rawNonce) {
        if (rawNonce == null || rawNonce.isBlank() || rawNonce.length() < 16 || rawNonce.length() > 256) {
            throw BusinessException.unauthorized();
        }
        try {
            Jwt jwt = decoder.decode(token);
            String nonce = jwt.getClaimAsString("nonce");
            if (!MessageDigest.isEqual(TokenHash.sha256(rawNonce).getBytes(StandardCharsets.US_ASCII),
                    nonce.getBytes(StandardCharsets.UTF_8))) {
                throw BusinessException.unauthorized();
            }
            return new Identity(jwt.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            throw BusinessException.unauthorized();
        }
    }

    public record Identity(String subject) {}
}

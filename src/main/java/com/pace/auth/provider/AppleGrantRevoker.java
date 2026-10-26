package com.pace.auth.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pace.common.exception.BusinessException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AppleGrantRevoker {
    private final String clientId;
    private final String clientSecret;
    private final URI tokenUri;
    private final URI revokeUri;
    private final AppleVerifier verifier;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired
    public AppleGrantRevoker(@Value("${pace.apple-client-id}") String clientId,
                             @Value("${pace.apple-client-secret}") String clientSecret,
                             AppleVerifier verifier, ObjectMapper json) {
        this(clientId, clientSecret, verifier, json,
                URI.create("https://appleid.apple.com/auth/token"),
                URI.create("https://appleid.apple.com/auth/revoke"));
    }

    AppleGrantRevoker(String clientId, String clientSecret, AppleVerifier verifier, ObjectMapper json,
                      URI tokenUri, URI revokeUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.verifier = verifier;
        this.json = json;
        this.tokenUri = tokenUri;
        this.revokeUri = revokeUri;
    }

    public void revoke(String code, String nonce, String expectedSubject) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "APPLE_REVOCATION_NOT_CONFIGURED",
                    "Apple 연결 해제 설정이 필요합니다. 계정은 삭제되지 않았습니다.");
        }
        try {
            HttpResponse<String> exchanged = post(tokenUri, Map.of(
                    "client_id", clientId, "client_secret", clientSecret,
                    "code", code, "grant_type", "authorization_code"));
            if (exchanged.statusCode() != 200) throw unavailable();
            JsonNode grant = json.readTree(exchanged.body());
            String idToken = text(grant, "id_token");
            String refreshToken = text(grant, "refresh_token");
            // 탈퇴 직전 다시 받은 Apple 인증 결과를 기존 연결 계정과 대조합니다.
            if (!verifier.verify(idToken, nonce).subject().equals(expectedSubject)) {
                throw BusinessException.unauthorized();
            }
            HttpResponse<String> revoked = post(revokeUri, Map.of(
                    "client_id", clientId, "client_secret", clientSecret,
                    "token", refreshToken, "token_type_hint", "refresh_token"));
            if (revoked.statusCode() != 200) throw unavailable();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException ex) {
            // Apple 응답·코드·토큰·client_secret을 예외 메시지나 로그에 포함하지 않습니다.
            throw unavailable();
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.path(field).isTextual() || node.path(field).asText().isBlank()) {
            throw unavailable();
        }
        return node.path(field).asText();
    }

    private HttpResponse<String> post(URI uri, Map<String, String> fields) throws IOException, InterruptedException {
        String body = fields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue())).collect(Collectors.joining("&"));
        return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private BusinessException unavailable() {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "APPLE_REVOCATION_FAILED",
                "Apple 연결 해제를 확인할 수 없습니다. Apple 재인증 후 다시 시도해 주세요. 계정은 삭제되지 않았습니다.");
    }
}

package com.pace.auth.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pace.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppleGrantRevokerTest {
    HttpServer server;
    AppleVerifier verifier;
    AppleGrantRevoker revoker;
    String grant = "{\"id_token\":\"verified-id-token\",\"refresh_token\":\"apple-refresh\"}";
    int exchangeStatus = 200;
    int revokeStatus = 200;
    List<String> requests;

    @BeforeEach
    void setup() throws Exception {
        requests = new ArrayList<>();
        verifier = mock(AppleVerifier.class);
        when(verifier.verify("verified-id-token", "raw-nonce-1234567890"))
                .thenReturn(new AppleVerifier.Identity("apple-subject"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().getPath() + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = (exchange.getRequestURI().getPath().equals("/token") ? grant : "")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(exchange.getRequestURI().getPath().equals("/token")
                    ? exchangeStatus : revokeStatus, response.length);
            try (var out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        revoker = revoker("signed-client-secret");
    }

    AppleGrantRevoker revoker(String secret) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new AppleGrantRevoker("com.hocompany.pace", secret, verifier, new ObjectMapper(),
                URI.create(base + "/token"), URI.create(base + "/revoke"));
    }

    @AfterEach
    void close() { server.stop(0); }

    @Test
    void 새_코드를_교환하고_계정과_nonce_검증후_갱신토큰을_폐기한다() {
        revoker.revoke("fresh code+", "raw-nonce-1234567890", "apple-subject");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0)).startsWith("/token ").contains("code=fresh+code%2B",
                "grant_type=authorization_code", "client_id=com.hocompany.pace", "client_secret=signed-client-secret");
        assertThat(requests.get(1)).startsWith("/revoke ").contains("token=apple-refresh", "token_type_hint=refresh_token");
        verify(verifier).verify("verified-id-token", "raw-nonce-1234567890");
    }

    @Test
    void 다른_Apple_계정이면_폐기하지_않는다() {
        assertThatThrownBy(() -> revoker.revoke("code", "raw-nonce-1234567890", "different-subject"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.status.value()).isEqualTo(401));
        assertThat(requests).hasSize(1);
    }

    @Test
    void nonce_검증실패면_폐기하지_않는다() {
        when(verifier.verify("verified-id-token", "wrong-nonce-1234567890")).thenThrow(BusinessException.unauthorized());
        assertThatThrownBy(() -> revoker.revoke("code", "wrong-nonce-1234567890", "apple-subject"))
                .isInstanceOf(BusinessException.class);
        assertThat(requests).hasSize(1);
    }

    @Test
    void 설정누락과_교환실패는_성공으로_간주하지_않는다() {
        assertThatThrownBy(() -> revoker("").revoke("code", "raw-nonce-1234567890", "apple-subject"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.status.value()).isEqualTo(503));
        assertThat(requests).isEmpty();
        exchangeStatus = 400;
        assertThatThrownBy(() -> revoker.revoke("code", "raw-nonce-1234567890", "apple-subject"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.status.value()).isEqualTo(502));
        assertThat(requests).hasSize(1);
    }

    @Test
    void 갱신토큰누락과_잘못된응답은_거절한다() {
        for (String response : List.of("{}", "null", "not-json", "{\"id_token\":\"verified-id-token\"}")) {
            grant = response;
            assertThatThrownBy(() -> revoker.revoke("code", "raw-nonce-1234567890", "apple-subject"))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.code).isEqualTo("APPLE_REVOCATION_FAILED"));
        }
        assertThat(requests).allMatch(request -> request.startsWith("/token "));
    }

    @Test
    void 폐기실패와_네트워크실패는_명시적으로_실패한다() {
        revokeStatus = 503;
        assertThatThrownBy(() -> revoker.revoke("code", "raw-nonce-1234567890", "apple-subject"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code).isEqualTo("APPLE_REVOCATION_FAILED");
                    assertThat(ex.getMessage()).doesNotContain("signed-client-secret", "apple-refresh", "verified-id-token");
                });
        server.stop(0);
        assertThatThrownBy(() -> revoker.revoke("code", "raw-nonce-1234567890", "apple-subject"))
                .isInstanceOf(BusinessException.class);
    }
}

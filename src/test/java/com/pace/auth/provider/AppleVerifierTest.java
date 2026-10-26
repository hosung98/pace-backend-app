package com.pace.auth.provider;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pace.common.exception.BusinessException;
import com.pace.common.util.TokenHash;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class AppleVerifierTest {
    static final String NONCE = "secure-random-raw-nonce-1234567890";
    static RSAKey key;
    static HttpServer server;
    static String jwks;
    AppleVerifier verifier;

    @BeforeAll
    static void keys() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("apple-test").generate();
        byte[] response = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/keys", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        jwks = "http://127.0.0.1:" + server.getAddress().getPort() + "/keys";
    }

    @BeforeEach
    void verifier() { verifier = new AppleVerifier(" com.hocompany.pace, another-client ", jwks); }

    @AfterAll
    static void close() { if (server != null) server.stop(0); }

    JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer("https://appleid.apple.com").subject("apple-subject")
                .audience("com.hocompany.pace").issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .claim("nonce", TokenHash.sha256(NONCE));
    }

    String signed(JWTClaimsSet claims, RSAKey signingKey, JWSAlgorithm algorithm, String kid) throws Exception {
        var jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(kid).build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    String signed(JWTClaimsSet.Builder claims) throws Exception {
        return signed(claims.build(), key, JWSAlgorithm.RS256, key.getKeyID());
    }

    @Test
    void 실제_JWKS_서명과_해시_nonce를_검증한다() throws Exception {
        assertThat(verifier.verify(signed(claims()), NONCE).subject()).isEqualTo("apple-subject");
        assertThat(verifier.verify(signed(claims().audience("another-client")), NONCE).subject())
                .isEqualTo("apple-subject");
        assertThatThrownBy(() -> new AppleVerifier(" , ", jwks)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 발급자_대상_만료_필수클레임과_nonce가_다르면_거절한다() throws Exception {
        for (var invalid : List.of(
                claims().issuer("https://attacker.example"),
                claims().issuer(null),
                claims().audience("other-client"),
                claims().audience(List.of()),
                claims().expirationTime(Date.from(Instant.now().minusSeconds(300))),
                claims().expirationTime(null),
                claims().notBeforeTime(Date.from(Instant.now().plusSeconds(300))),
                claims().subject(null),
                claims().subject(" "),
                claims().subject("x".repeat(256)),
                claims().claim("nonce", null),
                claims().claim("nonce", NONCE),
                claims().claim("nonce", TokenHash.sha256("another-raw-nonce")),
                claims().claim("nonce", List.of("wrong-type")))) {
            String token = signed(invalid);
            assertThatThrownBy(() -> verifier.verify(token, NONCE)).isInstanceOf(BusinessException.class);
        }
        String token = signed(claims());
        for (String raw : List.of("", "short", " ".repeat(32), "n".repeat(257), TokenHash.sha256(NONCE))) {
            assertThatThrownBy(() -> verifier.verify(token, raw)).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> verifier.verify(token, null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 다른서명_키_알고리즘과_손상된토큰은_거절한다() throws Exception {
        var rogue = new RSAKeyGenerator(2048).keyID(key.getKeyID()).generate();
        for (String token : List.of(
                signed(claims().build(), rogue, JWSAlgorithm.RS256, key.getKeyID()),
                signed(claims().build(), key, JWSAlgorithm.RS256, "unknown-key"),
                signed(claims().build(), key, JWSAlgorithm.RS512, key.getKeyID()),
                "broken.jwt.token")) {
            assertThatThrownBy(() -> verifier.verify(token, NONCE)).isInstanceOf(BusinessException.class);
        }
    }
}

package com.pace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pace.auth.provider.GoogleVerifier;
import com.pace.auth.provider.AppleVerifier;
import com.pace.auth.provider.AppleGrantRevoker;
import com.pace.user.scheduler.AccountCleanupScheduler;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {
    static final EmbeddedPostgres POSTGRES = database();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("pace.jwt-secret", () -> java.util.Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("pace.google-client-ids", () -> "test-client");
        registry.add("springdoc.api-docs.enabled", () -> "true");
        registry.add("springdoc.swagger-ui.enabled", () -> "true");
        registry.add("pace.account-cleanup-delay-ms", () -> "3600000");
    }

    static EmbeddedPostgres database() {
        try {
            var pg = EmbeddedPostgres.builder().setPort(0).start();
            new ResourceDatabasePopulator(new FileSystemResource("database/schema.sql")).execute(pg.getPostgresDatabase());
            new ResourceDatabasePopulator(new FileSystemResource("database/community-schema.sql"))
                    .execute(pg.getPostgresDatabase());
            return pg;
        } catch (IOException ex) {
            throw new IllegalStateException("테스트 PostgreSQL을 시작할 수 없습니다.", ex);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountCleanupScheduler cleanup;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder;
    @MockitoBean GoogleVerifier google;
    @MockitoBean AppleVerifier apple;
    @MockitoBean AppleGrantRevoker appleRevoker;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("TRUNCATE users CASCADE");
        when(google.verify("google-a")).thenReturn(new GoogleVerifier.Identity("subject-a", "테스트 러너"));
        when(google.verify("google-b")).thenReturn(new GoogleVerifier.Identity("subject-b", "다른 러너"));
        when(google.verify("google-c")).thenReturn(new GoogleVerifier.Identity("subject-c", "세 번째 러너"));
        when(apple.verify("apple-a", "raw-nonce-1234567890")).thenReturn(new AppleVerifier.Identity("subject-a"));
    }

    @AfterAll
    static void closeDatabase() throws IOException { POSTGRES.close(); }

    JsonNode login(String token) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/google").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("idToken", token))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    String bearer(JsonNode login) { return "Bearer " + login.get("accessToken").asText(); }

    JsonNode appleLogin(Map<String, String> body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/apple").contentType("application/json")
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void Apple_가입은_제공자별로_분리하고_닉네임은_최초에만_저장한다() throws Exception {
        JsonNode googleUser = login("google-a");
        JsonNode first = appleLogin(Map.of("idToken", "apple-a", "nonce", "raw-nonce-1234567890", "nickname", "애플 러너"));
        assertThat(first.get("isNewUser").asBoolean()).isTrue();
        assertThat(first.at("/user/id")).isNotEqualTo(googleUser.at("/user/id"));
        assertThat(first.get("accessTokenExpiresIn").asInt()).isEqualTo(1800);
        JsonNode again = appleLogin(Map.of("idToken", "apple-a", "nonce", "raw-nonce-1234567890", "nickname", "바뀐 이름"));
        assertThat(again.get("isNewUser").asBoolean()).isFalse();
        assertThat(again.at("/user/id")).isEqualTo(first.at("/user/id"));
        assertThat(again.at("/user/nickname").asText()).isEqualTo("애플 러너");
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(first)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.linkedProviders[0]").value("apple"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_settings", Integer.class)).isEqualTo(2);
        when(apple.verify("apple-b", "raw-nonce-1234567890")).thenReturn(new AppleVerifier.Identity("other-apple"));
        JsonNode fallback = appleLogin(Map.of("idToken", "apple-b", "nonce", "raw-nonce-1234567890"));
        assertThat(fallback.at("/user/nickname").asText()).isEqualTo("러너");
    }

    @Test
    void Apple도_회전_재사용탐지_로그아웃과_탈퇴_인증차단을_공유한다() throws Exception {
        Map<String, String> body = Map.of("idToken", "apple-a", "nonce", "raw-nonce-1234567890");
        JsonNode first = appleLogin(body);
        JsonNode rotated = json.readTree(mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("refreshToken", first.get("refreshToken").asText()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode token : List.of(first, rotated)) {
            mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                    .content(json.writeValueAsString(Map.of("refreshToken", token.get("refreshToken").asText()))))
                    .andExpect(status().isUnauthorized());
        }
        JsonNode separate = appleLogin(body);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(separate)).contentType("application/json")
                .content(json.writeValueAsString(Map.of("refreshToken", separate.get("refreshToken").asText()))))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content(json.writeValueAsString(Map.of("refreshToken", separate.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
        JsonNode deleting = appleLogin(body);
        mvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(deleting)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPLE_REAUTH_REQUIRED"));
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(deleting))).andExpect(status().isOk());
        String deletionBody = "{\"appleAuthorizationCode\":\"fresh-code\",\"appleNonce\":\"raw-nonce-1234567890\"}";
        doThrow(new com.pace.common.exception.BusinessException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                "APPLE_REVOCATION_FAILED", "연결 해제 실패"))
                .when(appleRevoker).revoke("fresh-code", "raw-nonce-1234567890", "subject-a");
        mvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(deleting))
                        .contentType("application/json").content(deletionBody))
                .andExpect(status().isBadGateway());
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(deleting))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM users", String.class)).isEqualTo("active");
        doNothing().when(appleRevoker).revoke("fresh-code", "raw-nonce-1234567890", "subject-a");
        mvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(deleting))
                        .contentType("application/json").content(deletionBody))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(deleting))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content(json.writeValueAsString(Map.of("refreshToken", deleting.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/apple").contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
        cleanup.purgeDeletedAccounts();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void Apple_잘못된_입력과_검증실패는_사용자를_생성하지_않는다() throws Exception {
        for (var body : List.of(
                Map.of("idToken", "apple-a"),
                Map.of("idToken", "", "nonce", "raw-nonce-1234567890"),
                Map.of("idToken", "x".repeat(16001), "nonce", "raw-nonce-1234567890"),
                Map.of("idToken", "apple-a", "nonce", "short"),
                Map.of("idToken", "apple-a", "nonce", "n".repeat(257)),
                Map.of("idToken", "apple-a", "nonce", "raw-nonce-1234567890", "nickname", " "),
                Map.of("idToken", "apple-a", "nonce", "raw-nonce-1234567890", "nickname", "a".repeat(31)),
                Map.of("idToken", "apple-a", "nonce", "raw-nonce-1234567890", "nickname", "  "))) {
            mvc.perform(post("/api/v1/auth/apple").contentType("application/json").content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        when(apple.verify("invalid", "raw-nonce-1234567890"))
                .thenThrow(com.pace.common.exception.BusinessException.unauthorized());
        mvc.perform(post("/api/v1/auth/apple").contentType("application/json")
                .content("{\"idToken\":\"invalid\",\"nonce\":\"raw-nonce-1234567890\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
    }

    @Test
    void Apple_수동_스키마_확장은_기존_사용자와_러닝을_보존한다() throws Exception {
        JsonNode existing = login("google-a");
        save(bearer(existing), run("before-apple", "2026-01-01T01:00:00Z", 1000, 300), 201);
        jdbc.execute("ALTER TABLE user_auth_providers DROP CONSTRAINT user_auth_providers_provider_check");
        jdbc.execute("ALTER TABLE user_auth_providers ADD CONSTRAINT user_auth_providers_provider_check CHECK (provider = 'google')");
        new ResourceDatabasePopulator(new FileSystemResource("database/apple-auth-schema.sql"))
                .execute(jdbc.getDataSource());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM runs", Integer.class)).isEqualTo(1);
        assertThat(login("google-a").at("/user/id")).isEqualTo(existing.at("/user/id"));
        appleLogin(Map.of("idToken", "apple-a", "nonce", "raw-nonce-1234567890"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(2);
    }

    Map<String, Object> run(String id, String start, int distance, int duration) {
        Instant instant = Instant.parse(start);
        return Map.of("clientRunId", id, "startedAt", start, "endedAt", instant.plusSeconds(duration).toString(),
                "durationSeconds", duration, "distanceMeters", distance, "routePoints", List.of());
    }

    JsonNode save(String token, Map<String, Object> body, int expectedStatus) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/runs").header("Authorization", token)
                        .header("Idempotency-Key", body.get("clientRunId"))
                        .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString());
    }

    @Test
    void 인증과_스웨거와_헬스체크() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED")).andExpect(jsonPath("$.requestId").isString());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/runs']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists());
        JsonNode first = login("google-a");
        assertThat(first.get("isNewUser").asBoolean()).isTrue();
        JsonNode again = login("google-a");
        assertThat(again.get("isNewUser").asBoolean()).isFalse();
        assertThat(again.at("/user/id")).isEqualTo(first.at("/user/id"));
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(first)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("테스트 러너"));
    }

    /** 실제 필터 체인에서 서명과 필수 클레임 검증이 유지되는지 확인합니다. */
    @Test
    void 잘못된_JWT는_공통_인증오류로_응답한다() throws Exception {
        JsonNode user = login("google-a");
        String subject = user.at("/user/id").asText();
        Instant now = Instant.now();
        var valid = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer("pace-api").subject(subject).audience(List.of("pace-app"))
                .issuedAt(now).expiresAt(now.plusSeconds(1800)).build();
        var expired = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer("pace-api").subject(subject).audience(List.of("pace-app"))
                .issuedAt(now.minusSeconds(600)).expiresAt(now.minusSeconds(300)).build();
        for (var claims : List.of(
                org.springframework.security.oauth2.jwt.JwtClaimsSet.from(valid).issuer("other-api").build(),
                org.springframework.security.oauth2.jwt.JwtClaimsSet.from(valid).audience(List.of("other-app")).build(),
                org.springframework.security.oauth2.jwt.JwtClaimsSet.from(valid).subject("unknown-user").build(),
                org.springframework.security.oauth2.jwt.JwtClaimsSet.from(valid).subject(" ").build(),
                org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                        .issuer("pace-api").subject(subject).audience(List.of("pace-app")).build())) {
            mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken(claims)))
                    .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.fieldErrors").isArray()).andExpect(jsonPath("$.requestId").isString());
        }
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken(expired)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        String signed = accessToken(valid);
        int signatureStart = signed.lastIndexOf('.') + 1;
        String tampered = signed.substring(0, signatureStart)
                + (signed.charAt(signatureStart) == 'A' ? "B" : "A") + signed.substring(signatureStart + 1);
        for (String token : List.of("not-a-jwt", tampered)) {
            mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        mvc.perform(get("/actuator/health").header("Authorization", "Bearer " + "not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    private String accessToken(org.springframework.security.oauth2.jwt.JwtClaimsSet claims) {
        return jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(
                        org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    /** DTO 바인딩 이후에도 기본값·빈 값·타입 오류 응답이 호환되는지 확인합니다. */
    @Test
    void 조회_DTO는_기존_쿼리_파라미터를_유지한다() throws Exception {
        String token = bearer(login("google-a"));
        for (String path : List.of("/api/v1/runs", "/api/v1/runs?limit=",
                "/api/v1/dashboard?timeZone=", "/api/v1/stats/running?period=&date=&timeZone=",
                "/api/v1/crews?query=러너&sido=서울&sigungu=강남구", "/api/v1/crews/rankings?period=",
                "/api/v1/races", "/api/v1/races?scope=&hideFull=",
                "/api/v1/races?scope=open&distanceMeters=5000&from=2026-01-01T00:00:00Z&to=2027-01-01T00:00:00Z&hideFull=true")) {
            mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk());
        }
        for (String path : List.of("/api/v1/runs?limit=abc", "/api/v1/runs?limit=0",
                "/api/v1/stats/running?date=not-a-date", "/api/v1/stats/running?period=invalid",
                "/api/v1/races?distanceMeters=abc", "/api/v1/races?from=not-an-instant",
                "/api/v1/races?hideFull=invalid")) {
            mvc.perform(get(path).header("Authorization", token)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors").isArray());
        }
    }

    @Test
    void 토큰회전과_재사용탐지_로그아웃() throws Exception {
        JsonNode first = login("google-a");
        JsonNode rotated = json.readTree(mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("refreshToken", first.get("refreshToken").asText()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content(json.writeValueAsString(Map.of("refreshToken", first.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content(json.writeValueAsString(Map.of("refreshToken", rotated.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
        JsonNode separate = login("google-a");
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(separate))
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("refreshToken", separate.get("refreshToken").asText()))))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content(json.writeValueAsString(Map.of("refreshToken", separate.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForList("SELECT token_hash FROM refresh_tokens", String.class))
                .allMatch(hash -> hash.matches("[a-f0-9]{64}"));
    }

    @Test
    void 부분수정과_입력검증과_탈퇴() throws Exception {
        String token = bearer(login("google-a"));
        mvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json")
                .content("{\"handle\":\"runner_01\",\"bio\":\"소개\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json")
                .content("{\"bio\":null}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").isEmpty()).andExpect(jsonPath("$.handle").value("runner_01"));
        mvc.perform(patch("/api/v1/users/me").header("Authorization", token).contentType("application/json")
                .content("{\"nickname\":null}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/users/me/settings").header("Authorization", token).contentType("application/json")
                .content("{\"weeklyGoalMeters\":25000,\"notifications\":{\"runReminder\":false}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.weeklyGoalMeters").value(25000))
                .andExpect(jsonPath("$.notifications.weeklySummary").value(true));
        for (String payload : List.of("{\"weeklyGoalMeters\":26000}", "{\"weeklyGoalMeters\":null}",
                "{\"timeZone\":\"bad/zone\"}", "{\"notifications\":{\"runReminderTime\":\"25:00\"}}",
                "{\"distanceUnit\":\"meters\"}", "{\"unknown\":true}")) {
            mvc.perform(patch("/api/v1/users/me/settings").header("Authorization", token)
                    .contentType("application/json").content(payload)).andExpect(status().isBadRequest());
        }
        save(token, run("deleted-user-run", "2026-01-01T01:00:00Z", 1000, 300), 201);
        mvc.perform(delete("/api/v1/users/me").header("Authorization", token)).andExpect(status().isAccepted());
        mvc.perform(get("/api/v1/users/me").header("Authorization", token)).andExpect(status().isUnauthorized());
        cleanup.purgeDeletedAccounts();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM runs", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
    }

    @Test
    void 기록_멱등성_권한_커서_삭제복구() throws Exception {
        String token = bearer(login("google-a"));
        var request = run("run-1", "2026-01-01T01:00:00Z", 5000, 1800);
        JsonNode first = save(token, request, 201);
        String id = first.get("id").asText();
        assertThat(first.get("averagePaceSecondsPerKm").asInt()).isEqualTo(360);
        assertThat(save(token, request, 200).get("id").asText()).isEqualTo(id);
        var changed = new java.util.HashMap<>(request);
        changed.put("distanceMeters", 6000);
        save(token, changed, 409);
        save(token, run("run-2", "2026-01-01T01:00:00Z", 1000, 400), 201);
        JsonNode page = json.readTree(mvc.perform(get("/api/v1/runs?limit=1").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(page.get("items").size()).isEqualTo(1);
        mvc.perform(get("/api/v1/runs").param("limit", "1").param("cursor", page.get("nextCursor").asText())
                .header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id)).andExpect(jsonPath("$.nextCursor").isEmpty());
        mvc.perform(get("/api/v1/runs?cursor=bad").header("Authorization", token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/runs?limit=51").header("Authorization", token)).andExpect(status().isBadRequest());
        String other = bearer(login("google-b"));
        mvc.perform(get("/api/v1/runs/" + id).header("Authorization", other)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/runs/" + id).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/runs/" + id).header("Authorization", token)).andExpect(status().isNotFound());
        save(token, request, 409);
        mvc.perform(post("/api/v1/runs/" + id + "/restore").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/runs/" + id).header("Authorization", token)).andExpect(status().isNoContent());
        jdbc.update("UPDATE runs SET deleted_at=now()-interval '25 hours' WHERE public_id=?", id);
        mvc.perform(post("/api/v1/runs/" + id + "/restore").header("Authorization", token)).andExpect(status().isConflict());
    }

    @Test
    void 경로축약과_전체경로와_시간검증() throws Exception {
        String token = bearer(login("google-a"));
        var body = new java.util.HashMap<>(run("route", "2026-01-01T01:00:00Z", 0, 1000));
        var points = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 401; i++) {
            points.add(Map.of("recordedAt", Instant.parse("2026-01-01T01:00:00Z").plusSeconds(i).toString(),
                    "latitude", 37.0 + i * 0.00001, "longitude", 127.0));
        }
        body.put("routePoints", points);
        JsonNode saved = save(token, body, 201);
        assertThat(saved.at("/route/coordinates").size()).isEqualTo(400);
        assertThat(saved.get("averagePaceSecondsPerKm").isNull()).isTrue();
        mvc.perform(get("/api/v1/runs/" + saved.get("id").asText()).param("route", "full")
                .header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.route.points.length()").value(401));
        body.put("clientRunId", "bad-time");
        body.put("durationSeconds", 2000);
        save(token, body, 400);
        body.put("durationSeconds", 1000);
        body.put("routePoints", List.of(Map.of("recordedAt", "2026-01-01T01:00:00Z", "latitude", 100, "longitude", 127)));
        save(token, body, 400);
    }

    @Test
    void 시간대_경계와_주월통계와_삭제제외() throws Exception {
        String token = bearer(login("google-a"));
        save(token, run("sunday", "2026-01-04T14:59:00Z", 2000, 600), 201);
        JsonNode monday = save(token, run("monday", "2026-01-04T15:00:00Z", 5000, 1800), 201);
        save(token, run("next-week", "2026-01-11T15:00:00Z", 1000, 400), 201);
        mvc.perform(get("/api/v1/stats/running").header("Authorization", token)
                .param("period", "week").param("date", "2026-01-05").param("timeZone", "Asia/Seoul"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totals.distanceMeters").value(5000))
                .andExpect(jsonPath("$.totals.averagePaceSecondsPerKm").value(360))
                .andExpect(jsonPath("$.buckets.length()").value(7))
                .andExpect(jsonPath("$.buckets[0].distanceMeters").value(5000));
        mvc.perform(get("/api/v1/stats/running").header("Authorization", token)
                .param("period", "month").param("date", "2026-01-05"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totals.distanceMeters").value(8000))
                .andExpect(jsonPath("$.buckets.length()").value(5))
                .andExpect(jsonPath("$.totals.averagePaceSecondsPerKm").value(350));
        mvc.perform(delete("/api/v1/runs/" + monday.get("id").asText()).header("Authorization", token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/stats/running").header("Authorization", token)
                .param("date", "2026-01-05")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.runCount").value(0));
        mvc.perform(get("/api/v1/dashboard").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.week.dailyDistances.length()").value(7));
    }

    @Test
    void 크루_생성_검색_실제기록_가입신청_승인_권한_양도와_쿨다운() throws Exception {
        JsonNode leader = login("google-a");
        JsonNode applicant = login("google-b");
        String leaderToken = bearer(leader);
        String applicantToken = bearer(applicant);
        JsonNode created = json.readTree(mvc.perform(post("/api/v1/crews").header("Authorization", leaderToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("name", "아침 러너", "intro", "한강에서 달려요",
                                "region", Map.of("sido", "서울", "sigungu", "영등포구")))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String crewId = created.get("id").asText();
        mvc.perform(get("/api/v1/users/me/crew").header("Authorization", leaderToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.crewId").value(crewId));
        mvc.perform(get("/api/v1/crews").header("Authorization", applicantToken).param("query", "아침")
                        .param("sido", "서울").param("sigungu", "영등포구"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(crewId))
                .andExpect(jsonPath("$[0].weekDistanceMeters").value(0))
                .andExpect(jsonPath("$[0].isMine").value(false));
        mvc.perform(get("/api/v1/crews/rankings").header("Authorization", applicantToken).param("period", "week"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(crewId))
                .andExpect(jsonPath("$[0].distanceMeters").value(0))
                .andExpect(jsonPath("$[0].isMine").value(false));
        mvc.perform(post("/api/v1/crews/" + crewId + "/join-requests").header("Authorization", applicantToken))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/crews/" + crewId + "/join-requests").header("Authorization", applicantToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/crews/" + crewId + "/join-requests").header("Authorization", applicantToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/crews/" + crewId + "/join-requests").header("Authorization", leaderToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].userId").value(applicant.at("/user/id").asText()));
        mvc.perform(post("/api/v1/crews/" + crewId + "/join-requests/" + applicant.at("/user/id").asText()
                        + "/approve").header("Authorization", leaderToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/crews/" + crewId + "?period=week").header("Authorization", applicantToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.membership").value("member"))
                .andExpect(jsonPath("$.inviteCode").isEmpty()).andExpect(jsonPath("$.pendingRequestCount").value(0))
                .andExpect(jsonPath("$.members.length()").value(2));
        save(leaderToken, run("crew-real-run", Instant.now().toString(), 4200, 0), 201);
        mvc.perform(get("/api/v1/crews/" + crewId + "?period=week").header("Authorization", leaderToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.distanceMeters").value(4200))
                .andExpect(jsonPath("$.members[0].distanceMeters").value(4200))
                .andExpect(jsonPath("$.members[0].runCount").value(1))
                .andExpect(jsonPath("$.members[0].isMe").value(true));
        mvc.perform(post("/api/v1/crews/" + crewId + "/leader").header("Authorization", leaderToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("userId", applicant.at("/user/id").asText()))))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/crews/" + crewId + "/members/me").header("Authorization", leaderToken))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/crews/" + crewId + "/members/me").header("Authorization", applicantToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me/crew").header("Authorization", applicantToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.crewId").isEmpty());
        mvc.perform(post("/api/v1/crews").header("Authorization", applicantToken).contentType("application/json")
                        .content("{\"name\":\"다른 크루\",\"intro\":\"\",\"region\":null}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void 크루_초대코드_가입과_리더전용_재발급() throws Exception {
        JsonNode leader = login("google-a");
        JsonNode member = login("google-b");
        String leaderToken = bearer(leader);
        String memberToken = bearer(member);
        JsonNode created = json.readTree(mvc.perform(post("/api/v1/crews").header("Authorization", leaderToken)
                        .contentType("application/json")
                        .content("{\"name\":\"초대 크루\",\"intro\":\"코드 가입\",\"region\":null}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String crewId = created.get("id").asText();
        mvc.perform(post("/api/v1/crews/" + crewId + "/invite-code").header("Authorization", memberToken))
                .andExpect(status().isForbidden());
        JsonNode code = json.readTree(mvc.perform(post("/api/v1/crews/" + crewId + "/invite-code")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(code.get("inviteCode").asText()).matches("[A-HJ-NP-Z2-9]{8}");
        mvc.perform(post("/api/v1/crews/join-by-code").header("Authorization", memberToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("code", code.get("inviteCode").asText()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(crewId));
    }

    @Test
    void 대결_공개_비공개_정원_참가_초대코드와_자동마감() throws Exception {
        JsonNode host = login("google-a");
        JsonNode runner = login("google-b");
        String hostToken = bearer(host);
        String runnerToken = bearer(runner);
        String startsAt = Instant.now().plusSeconds(7200).toString();
        JsonNode created = json.readTree(mvc.perform(post("/api/v1/races").header("Authorization", hostToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("title", "저녁 5km", "distanceMeters", 5000,
                                "startsAt", startsAt, "joinDeadlineMinutes", 30, "minParticipants", 2,
                                "maxParticipants", 2, "visibility", "public"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String raceId = created.get("id").asText();
        mvc.perform(get("/api/v1/races/" + raceId).header("Authorization", hostToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isHost").value(true))
                .andExpect(jsonPath("$.isJoined").value(true)).andExpect(jsonPath("$.participantCount").value(1))
                .andExpect(jsonPath("$.participants[0].isHost").value(true))
                .andExpect(jsonPath("$.participants[0].isMe").value(true))
                .andExpect(jsonPath("$.participants[0].result").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(post("/api/v1/races/" + raceId + "/participants").header("Authorization", runnerToken))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/races/" + raceId + "/participants/me").header("Authorization", runnerToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/races/" + raceId).header("Authorization", hostToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participantCount").value(1));
        mvc.perform(post("/api/v1/races/" + raceId + "/participants").header("Authorization", runnerToken))
                .andExpect(status().isNoContent());
        JsonNode detail = json.readTree(mvc.perform(get("/api/v1/races/" + raceId).header("Authorization", runnerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isJoined").value(true))
                .andExpect(jsonPath("$.isHost").value(false)).andExpect(jsonPath("$.participantCount").value(2))
                .andReturn().getResponse().getContentAsString());
        assertThat(detail.at("/participants/1/isMe").asBoolean()).isTrue();
        mvc.perform(get("/api/v1/races").header("Authorization", runnerToken).param("scope", "open")
                        .param("distanceMeters", "5000").param("from", Instant.now().toString())
                        .param("to", Instant.now().plusSeconds(10800).toString()).param("hideFull", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(raceId))
                .andExpect(jsonPath("$[0].isJoined").value(true));
        JsonNode privateRace = json.readTree(mvc.perform(post("/api/v1/races").header("Authorization", hostToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("title", "초대 하프", "distanceMeters", 21097,
                                "startsAt", startsAt, "joinDeadlineMinutes", 60, "minParticipants", 2,
                                "maxParticipants", 6, "visibility", "private"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String privateId = privateRace.get("id").asText();
        mvc.perform(get("/api/v1/races/" + privateId).header("Authorization", runnerToken))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/races/" + privateId + "/participants").header("Authorization", runnerToken))
                .andExpect(status().isNotFound());
        JsonNode privateDetail = json.readTree(mvc.perform(get("/api/v1/races/" + privateId).header("Authorization", hostToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(privateDetail.get("inviteCode").asText()).matches("[A-HJ-NP-Z2-9]{8}");
        mvc.perform(post("/api/v1/races/join-by-code").header("Authorization", runnerToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("code", privateDetail.get("inviteCode").asText()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(privateId));
        JsonNode third = login("google-c");
        mvc.perform(post("/api/v1/races/" + raceId + "/participants").header("Authorization", bearer(third)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/races/" + raceId + "/cancel").header("Authorization", runnerToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/races/" + raceId + "/cancel").header("Authorization", hostToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void 대결_허용값_검증과_마감_시점_인원은_계정삭제에도_고정() throws Exception {
        JsonNode host = login("google-a");
        JsonNode runner = login("google-b");
        String hostToken = bearer(host);
        String runnerToken = bearer(runner);
        String startsAt = Instant.now().plusSeconds(7200).toString();
        mvc.perform(post("/api/v1/races").header("Authorization", hostToken).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("title", "잘못된 거리", "distanceMeters", 4000,
                                "startsAt", startsAt, "joinDeadlineMinutes", 30, "minParticipants", 2,
                                "maxParticipants", 4, "visibility", "public"))))
                .andExpect(status().isBadRequest());
        JsonNode created = json.readTree(mvc.perform(post("/api/v1/races").header("Authorization", hostToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("title", "최소 인원 미달", "distanceMeters", 3000,
                                "startsAt", startsAt, "joinDeadlineMinutes", 30, "minParticipants", 3,
                                "maxParticipants", 6, "visibility", "public"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String raceId = created.get("id").asText();
        mvc.perform(post("/api/v1/races/" + raceId + "/participants").header("Authorization", runnerToken))
                .andExpect(status().isNoContent());
        Long runnerId = jdbc.queryForObject("SELECT id FROM users WHERE public_id=?",
                Long.class, runner.at("/user/id").asText());
        jdbc.update("UPDATE races SET starts_at=now() WHERE public_id=?", raceId);
        mvc.perform(delete("/api/v1/races/" + raceId + "/participants/me").header("Authorization", runnerToken))
                .andExpect(status().isConflict());
        jdbc.update("DELETE FROM users WHERE id=?", runnerId);
        mvc.perform(get("/api/v1/races/" + raceId).header("Authorization", hostToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelled").value(true))
                .andExpect(jsonPath("$.participantCount").value(2));
    }
}

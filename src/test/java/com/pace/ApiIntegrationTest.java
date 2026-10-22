package com.pace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pace.auth.provider.GoogleVerifier;
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
            return pg;
        } catch (IOException ex) {
            throw new IllegalStateException("테스트 PostgreSQL을 시작할 수 없습니다.", ex);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountCleanupScheduler cleanup;
    @MockitoBean GoogleVerifier google;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("TRUNCATE users CASCADE");
        when(google.verify("google-a")).thenReturn(new GoogleVerifier.Identity("subject-a", "테스트 러너"));
        when(google.verify("google-b")).thenReturn(new GoogleVerifier.Identity("subject-b", "다른 러너"));
    }

    @AfterAll
    static void closeDatabase() throws IOException { POSTGRES.close(); }

    JsonNode login(String token) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/google").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("idToken", token))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    String bearer(JsonNode login) { return "Bearer " + login.get("accessToken").asText(); }

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
}

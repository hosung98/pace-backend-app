package com.pace.run.service.impl;

import com.pace.common.util.TokenHash;
import com.pace.common.exception.BusinessException;
import com.pace.common.util.JsonCodec;
import com.pace.user.service.UserAccess;
import com.pace.run.dto.*;
import com.pace.run.entity.*;
import com.pace.run.repository.*;
import com.pace.run.mapper.*;
import com.pace.run.service.RunService;
import com.pace.run.util.RouteGeometry;
import com.pace.run.vo.IdempotencyKey;
import com.pace.run.vo.RunCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunServiceImpl implements RunService {
    private final RunRepository runs;
    private final RouteRepository routes;
    private final RequestKeyRepository keys;
    private final RunQueryMapper queries;
    private final UserAccess access;
    private final JsonCodec json;

    public RunServiceImpl(RunRepository runs, RouteRepository routes, RequestKeyRepository keys,
                      RunQueryMapper queries, UserAccess access, JsonCodec json) {
        this.runs = runs;
        this.routes = routes;
        this.keys = keys;
        this.queries = queries;
        this.access = access;
        this.json = json;
    }

    @Transactional
    public CreateRunResult create(String subject, CreateRunRequest request, String key) {
        RouteGeometry.validate(request);
        IdempotencyKey idempotencyKey = IdempotencyKey.fromNullable(key);
        String keyValue = idempotencyKey == null ? null : idempotencyKey.value();
        // 사용자별 쓰기를 직렬화하여 여러 서버에서도 멱등성과 탈퇴 처리를 보장합니다.
        long userId = access.id(subject, true);
        String hash = TokenHash.sha256(json.write(request));
        if (keyValue != null) {
            var saved = keys.findById(new RunRequestKey.Key(userId, keyValue));
            if (saved.isPresent()) return replay(runs.findById(saved.get().getRunId()).orElseThrow(), hash);
        }
        var existing = runs.findByUserIdAndClientRunId(userId, request.clientRunId());
        if (existing.isPresent()) {
            CreateRunResult replay = replay(existing.get(), hash);
            if (keyValue != null) keys.save(new RunRequestKey(userId, keyValue, existing.get().getId()));
            return replay;
        }
        Run run = runs.saveAndFlush(new Run(userId, request, hash));
        RunRoute route = routes.save(new RunRoute(run.getId(), request.routePoints()));
        if (keyValue != null) keys.save(new RunRequestKey(userId, keyValue, run.getId()));
        double gpsDistance = RouteGeometry.distance(request.routePoints());
        if (request.routePoints().size() > 1
                && Math.abs(gpsDistance - request.distanceMeters()) > Math.max(1, request.distanceMeters() * 0.1)) {
            LoggerFactory.getLogger(RunServiceImpl.class).warn(
                    "클라이언트 거리와 GPS 계산 거리가 10% 이상 다릅니다. runId={}, clientMeters={}, gpsMeters={}",
                    run.getPublicId(), request.distanceMeters(), Math.round(gpsDistance));
        }
        return new CreateRunResult(true, detail(run, route.response("simplified")));
    }

    private CreateRunResult replay(Run run, String hash) {
        if (!hash.equals(run.getRequestHash())) throw BusinessException.conflict("기존 요청과 기록 내용이 다릅니다.");
        if (run.getDeletedAt() != null) throw BusinessException.conflict("삭제된 기록입니다. 먼저 복구해 주세요.");
        return new CreateRunResult(false, detail(run, routes.findById(run.getId()).orElseThrow().response("simplified")));
    }

    @Transactional(readOnly = true)
    public RunListResponse list(String subject, String cursor, int limit) {
        if (limit < 1 || limit > 50) throw BusinessException.invalid("limit은 1~50 범위여야 합니다.");
        long userId = access.id(subject, false);
        RunCursor after = cursor == null ? null : decode(cursor);
        List<RunRow> rows = queries.list(userId, after == null ? null : after.startedAt(),
                after == null ? null : after.id(), limit + 1);
        boolean more = rows.size() > limit;
        List<RunRow> page = rows.subList(0, Math.min(limit, rows.size()));
        String next = more ? encode(page.getLast()) : null;
        return new RunListResponse(page.stream().map(RunRow::toResponse).toList(), next);
    }

    @Transactional(readOnly = true)
    public RunDetailResponse detail(String subject, String publicId, String mode) {
        if (!Set.of("simplified", "full", "none").contains(mode)) throw BusinessException.invalid("경로 형식이 올바르지 않습니다.");
        Run run = find(access.id(subject, false), publicId, false);
        return detail(run, mode.equals("none") ? null : routes.findById(run.getId()).orElseThrow().response(mode));
    }

    @Transactional
    public RunDetailResponse title(String subject, String publicId, String title) {
        Run run = find(access.id(subject, true), publicId, false);
        run.setTitle(title);
        return detail(run, routes.findById(run.getId()).orElseThrow().response("simplified"));
    }

    @Transactional
    public void delete(String subject, String publicId) {
        find(access.id(subject, true), publicId, true).delete();
    }

    @Transactional
    public RunDetailResponse restore(String subject, String publicId) {
        Run run = find(access.id(subject, true), publicId, true);
        if (run.getDeletedAt() != null && run.getDeletedAt().isBefore(Instant.now().minus(24, ChronoUnit.HOURS))) {
            throw BusinessException.conflict("기록은 삭제 후 24시간 이내에만 복구할 수 있습니다.");
        }
        run.restore();
        return detail(run, routes.findById(run.getId()).orElseThrow().response("simplified"));
    }

    private Run find(long userId, String id, boolean includeDeleted) {
        Run run = runs.findByUserIdAndPublicId(userId, id).orElseThrow(BusinessException::missing);
        if (!includeDeleted && run.getDeletedAt() != null) throw BusinessException.missing();
        return run;
    }

    private RunDetailResponse detail(Run run, RunRouteResponse route) {
        RunSummaryResponse summary = run.summary();
        Double speed = summary.durationSeconds() == 0 ? null
                : Math.round(summary.distanceMeters() * 3.6 / summary.durationSeconds() * 10) / 10.0;
        return new RunDetailResponse(summary.id(), summary.clientRunId(), summary.title(), summary.startedAt(), summary.endedAt(),
                summary.durationSeconds(), summary.distanceMeters(), summary.averagePaceSecondsPerKm(),
                speed, summary.routePointCount(), route);
    }

    private String encode(RunRow row) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (row.startedAt() + "|" + row.internalId()).getBytes(StandardCharsets.UTF_8));
    }

    private RunCursor decode(String cursor) {
        if (cursor.length() > 256) throw BusinessException.invalid("커서 형식이 올바르지 않습니다.");
        try {
            String[] values = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (values.length != 2) throw new IllegalArgumentException();
            long id = Long.parseLong(values[1]);
            if (id <= 0) throw new IllegalArgumentException();
            return new RunCursor(Instant.parse(values[0]), id);
        } catch (IllegalArgumentException | java.time.DateTimeException ex) {
            throw BusinessException.invalid("커서 형식이 올바르지 않습니다.");
        }
    }

}

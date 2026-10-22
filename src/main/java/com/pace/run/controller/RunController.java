package com.pace.run.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.pace.run.dto.*;
import com.pace.run.service.RunService;

/** 러닝 요청을 검증하고 비즈니스 처리는 서비스에 위임합니다. */
@RestController
@RequestMapping("/api/v1/runs")
@Tag(name = "러닝", description = "본인 기록 저장·조회·삭제·복구")
public class RunController {
    private final RunService service;
    public RunController(RunService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "러닝 저장", description = "최초 저장은 201, 동일한 재요청은 200, 내용이 다른 재요청은 409를 반환합니다.")
    public ResponseEntity<RunDetailResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRunRequest request,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        CreateRunResult result = service.create(jwt.getSubject(), request, key);
        return ResponseEntity.status(result.created() ? 201 : 200)
                .location(URI.create("/api/v1/runs/" + result.body().id())).body(result.body());
    }

    /** 쿼리 문자열을 목록 DTO로 바인딩하며 기존 기본값을 유지합니다. */
    @GetMapping
    @Operation(summary = "러닝 목록", description = "시작 시각 내림차순 커서 페이지네이션입니다. 경로 데이터는 제외합니다.")
    public RunListResponse list(@AuthenticationPrincipal Jwt jwt,
                              @org.springdoc.core.annotations.ParameterObject @ModelAttribute RunListQuery query) {
        return service.list(jwt.getSubject(), query.cursor(), query.limit());
    }

    @GetMapping("/{runId}")
    @Operation(summary = "러닝 상세", description = "route는 simplified(기본), full, none 중 선택합니다. 좌표는 [위도, 경도] 순서입니다.")
    public RunDetailResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable String runId,
                         @org.springdoc.core.annotations.ParameterObject @ModelAttribute RunDetailQuery query) {
        return service.detail(jwt.getSubject(), runId, query.route());
    }

    @PatchMapping("/{runId}")
    @Operation(summary = "러닝 제목 수정")
    public RunDetailResponse title(@AuthenticationPrincipal Jwt jwt, @PathVariable String runId,
                        @Valid @RequestBody UpdateRunTitleRequest request) {
        return service.title(jwt.getSubject(), runId, request.title());
    }

    @DeleteMapping("/{runId}")
    @Operation(summary = "러닝 소프트 삭제")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String runId) {
        service.delete(jwt.getSubject(), runId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{runId}/restore")
    @Operation(summary = "삭제 후 24시간 이내 러닝 복구")
    public RunDetailResponse restore(@AuthenticationPrincipal Jwt jwt, @PathVariable String runId) {
        return service.restore(jwt.getSubject(), runId);
    }
}

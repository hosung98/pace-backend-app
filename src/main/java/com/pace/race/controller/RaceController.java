package com.pace.race.controller;

import com.pace.race.dto.*;
import com.pace.race.service.RaceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** 대결 API의 요청 바인딩과 HTTP 응답을 담당합니다. */
@RestController
@RequestMapping("/api/v1/races")
public class RaceController {
    private final RaceService service;

    public RaceController(RaceService service) { this.service = service; }

    /** 목록 필터를 묶어 받고 대결 조회는 서비스에 위임합니다. */
    @GetMapping
    public List<RaceSummaryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                          @ParameterObject @ModelAttribute RaceListQuery query) {
        return service.list(jwt.getSubject(), query.scope(), query.distanceMeters(),
                query.from(), query.to(), query.hideFull());
    }

    @GetMapping("/{raceId}")
    public RaceDetailResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable String raceId) {
        return service.detail(jwt.getSubject(), raceId);
    }

    @PostMapping
    public ResponseEntity<RaceIdResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CreateRaceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(jwt.getSubject(), request));
    }

    @PatchMapping("/{raceId}")
    public ResponseEntity<Void> update(@AuthenticationPrincipal Jwt jwt, @PathVariable String raceId,
                                       @Valid @RequestBody CreateRaceRequest request) {
        service.update(jwt.getSubject(), raceId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{raceId}/participants")
    public ResponseEntity<Void> join(@AuthenticationPrincipal Jwt jwt, @PathVariable String raceId) {
        service.join(jwt.getSubject(), raceId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{raceId}/participants/me")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal Jwt jwt, @PathVariable String raceId) {
        service.leave(jwt.getSubject(), raceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/join-by-code")
    public RaceIdResponse joinByCode(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody RaceJoinByCodeRequest request) {
        return service.joinByCode(jwt.getSubject(), request.code());
    }

    @PostMapping("/{raceId}/cancel")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String raceId) {
        service.cancel(jwt.getSubject(), raceId);
        return ResponseEntity.noContent().build();
    }
}

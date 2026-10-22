package com.pace.race.controller;

import com.pace.race.dto.*;
import com.pace.race.service.RaceService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/races")
public class RaceController {
    private final RaceService service;

    public RaceController(RaceService service) { this.service = service; }

    @GetMapping
    public List<RaceSummaryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam(defaultValue = "open") String scope,
                                          @RequestParam(required = false) Integer distanceMeters,
                                          @RequestParam(required = false) Instant from,
                                          @RequestParam(required = false) Instant to,
                                          @RequestParam(defaultValue = "false") boolean hideFull) {
        return service.list(jwt.getSubject(), scope, distanceMeters, from, to, hideFull);
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

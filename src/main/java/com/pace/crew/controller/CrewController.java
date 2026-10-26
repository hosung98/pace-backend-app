package com.pace.crew.controller;

import com.pace.crew.dto.*;
import com.pace.crew.service.CrewService;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** 크루 API의 입력과 응답을 담당하며 권한 판단은 서비스에 위임합니다. */
@RestController
@RequestMapping("/api/v1")
public class CrewController {
    private final CrewService service;

    public CrewController(CrewService service) { this.service = service; }

    @GetMapping("/crews/rankings")
    public List<CrewRankingResponse> rankings(@AuthenticationPrincipal Jwt jwt,
                                               @ParameterObject @ModelAttribute CrewPeriodQuery query) {
        return service.rankings(jwt.getSubject(), query.period());
    }

    /** 검색 조건을 DTO로 바인딩하되 쿼리 파라미터 이름은 유지합니다. */
    @GetMapping("/crews")
    public List<CrewSearchResponse> search(@AuthenticationPrincipal Jwt jwt,
                                           @ParameterObject @ModelAttribute CrewSearchQuery query) {
        return service.search(jwt.getSubject(), query.query(), query.sido(), query.sigungu());
    }

    @GetMapping("/users/me/crew")
    public MyCrewResponse myCrew(@AuthenticationPrincipal Jwt jwt) {
        return service.myCrew(jwt.getSubject());
    }

    @GetMapping("/crews/{crewId}")
    public CrewDetailResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable String crewId,
                                     @ParameterObject @ModelAttribute CrewPeriodQuery query) {
        return service.detail(jwt.getSubject(), crewId, query.period());
    }

    @PostMapping("/crews")
    public ResponseEntity<CrewIdResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CreateCrewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(jwt.getSubject(), request));
    }

    @PostMapping("/crews/join-by-code")
    public CrewIdResponse joinByCode(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JoinByCodeRequest request) {
        return service.joinByCode(jwt.getSubject(), request.code());
    }

    @PostMapping("/crews/{crewId}/join-requests")
    public ResponseEntity<Void> requestJoin(@AuthenticationPrincipal Jwt jwt, @PathVariable String crewId) {
        service.requestJoin(jwt.getSubject(), crewId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/crews/join-requests/me")
    public ResponseEntity<Void> cancelJoinRequest(@AuthenticationPrincipal Jwt jwt) {
        service.cancelMyRequest(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/crews/{crewId}/members/me")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal Jwt jwt, @PathVariable String crewId) {
        service.leave(jwt.getSubject(), crewId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/crews/{crewId}/invite-code")
    public InviteCodeResponse regenerateInviteCode(@AuthenticationPrincipal Jwt jwt, @PathVariable String crewId) {
        return service.regenerateInviteCode(jwt.getSubject(), crewId);
    }

    @GetMapping("/crews/{crewId}/join-requests")
    public List<CrewJoinRequestResponse> joinRequests(@AuthenticationPrincipal Jwt jwt, @PathVariable String crewId) {
        return service.joinRequests(jwt.getSubject(), crewId);
    }

    @PostMapping("/crews/{crewId}/join-requests/{userId}/approve")
    public ResponseEntity<Void> approve(@AuthenticationPrincipal Jwt jwt, @PathVariable String crewId,
                                        @PathVariable String userId) {
        service.approve(jwt.getSubject(), crewId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/crews/{crewId}/join-requests/{userId}/reject")
    public ResponseEntity<Void> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable String crewId,
                                       @PathVariable String userId) {
        service.reject(jwt.getSubject(), crewId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/crews/{crewId}/leader")
    public ResponseEntity<Void> transferLeadership(@AuthenticationPrincipal Jwt jwt, @PathVariable String crewId,
                                                   @Valid @RequestBody LeaderTransferRequest request) {
        service.transferLeadership(jwt.getSubject(), crewId, request.userId());
        return ResponseEntity.noContent().build();
    }

}

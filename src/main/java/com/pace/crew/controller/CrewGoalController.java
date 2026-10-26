package com.pace.crew.controller;

import com.pace.crew.dto.CrewGoalResponse;
import com.pace.crew.dto.UpdateCrewGoalRequest;
import com.pace.crew.service.CrewGoalService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/crews/{crewId}/goal")
public class CrewGoalController {
    private final CrewGoalService service;

    public CrewGoalController(CrewGoalService service) { this.service = service; }

    @GetMapping
    public CrewGoalResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String crewId) {
        return service.get(jwt.getSubject(), crewId);
    }

    @PutMapping
    public CrewGoalResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String crewId,
                                   @Valid @RequestBody UpdateCrewGoalRequest request) {
        return service.update(jwt.getSubject(), crewId, request.targetKm());
    }
}

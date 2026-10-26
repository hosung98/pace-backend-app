package com.pace.progress.controller;

import com.pace.progress.dto.UserProgressResponse;
import com.pace.progress.dto.RepresentativeBadgeRequest;
import com.pace.progress.service.ProgressService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/users/me/progress")
public class ProgressController {
    private final ProgressService service;

    public ProgressController(ProgressService service) { this.service = service; }

    @GetMapping
    public UserProgressResponse progress(@AuthenticationPrincipal Jwt jwt) {
        return service.progress(jwt.getSubject());
    }

    @PutMapping("/representative-badge")
    public UserProgressResponse representativeBadge(@AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody RepresentativeBadgeRequest request) {
        return service.setRepresentativeBadge(jwt.getSubject(), request.badgeId());
    }
}

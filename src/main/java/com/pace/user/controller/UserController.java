package com.pace.user.controller;

import com.pace.user.dto.*;
import com.pace.user.service.UserService;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "내 정보·설정")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "내 프로필 조회")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return service.me(jwt.getSubject());
    }

    @PatchMapping
    @Operation(summary = "프로필 부분 수정", description = "handle·bio는 명시적으로 null을 보내면 삭제합니다. 생략한 필드는 유지됩니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content =
            @Content(examples = @ExampleObject(value = "{\"nickname\":\"Runner\",\"handle\":\"runner_01\",\"bio\":null,\"profileVisibility\":\"public\"}"))))
    public MeResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest patch) {
        return service.update(jwt.getSubject(), patch);
    }

    @DeleteMapping
    @Operation(summary = "회원 탈퇴", description = "즉시 인증을 차단하고 배치로 계정과 기록을 영구 삭제합니다.")
    public ResponseEntity<?> delete(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        service.delete(jwt.getSubject());
        return ResponseEntity.accepted().body(Map.of("requestId", request.getAttribute("requestId")));
    }

    @GetMapping("/settings")
    @Operation(summary = "내 설정 조회")
    public SettingsResponse settings(@AuthenticationPrincipal Jwt jwt) {
        return service.settings(jwt.getSubject());
    }

    @PatchMapping("/settings")
    @Operation(summary = "설정 부분 수정", description = "생략한 필드는 유지됩니다. 알림 설정 저장만 제공하며 실제 발송은 후속 범위입니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content =
            @Content(examples = @ExampleObject(value = "{\"weeklyGoalMeters\":25000,\"timeZone\":\"Asia/Seoul\",\"notifications\":{\"runReminder\":true,\"runReminderTime\":\"07:00\"}}"))))
    public SettingsResponse settings(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateSettingsRequest patch) {
        return service.updateSettings(jwt.getSubject(), patch);
    }
}

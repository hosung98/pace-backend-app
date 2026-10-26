package com.pace.auth.controller;

import com.pace.auth.dto.*;
import com.pace.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "인증", description = "Google·Apple 로그인과 토큰 갱신·로그아웃")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/google")
    @SecurityRequirements
    @Operation(summary = "Google ID 토큰 검증 후 로그인 또는 가입")
    public AuthTokenResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        return service.login(request.idToken());
    }

    @PostMapping("/apple")
    @SecurityRequirements
    @Operation(summary = "Apple ID 토큰과 nonce 검증 후 로그인 또는 가입",
            description = "nonce는 원문(16~256자)입니다. Apple 요청에는 SHA-256 소문자 16진수 해시를 전달합니다. nickname은 최초 가입에만 사용합니다.")
    public AuthTokenResponse apple(@Valid @RequestBody AppleLoginRequest request) {
        return service.loginApple(request.idToken(), request.nonce(), request.nickname());
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "갱신 토큰 회전", description = "이미 사용한 토큰을 재사용하면 같은 계열의 갱신 토큰을 전부 폐기합니다.")
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return service.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "해당 세션의 갱신 토큰 계열을 폐기합니다. 이미 발급된 접근 토큰은 최대 30분간 유효합니다.")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody RefreshTokenRequest request) {
        service.logout(jwt.getSubject(), request.refreshToken());
        return ResponseEntity.noContent().build();
    }

}

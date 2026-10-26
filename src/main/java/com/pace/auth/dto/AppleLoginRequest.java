package com.pace.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppleLoginRequest(
        @NotBlank @Size(max = 16000) String idToken,
        @NotBlank @Size(min = 16, max = 256) String nonce,
        @Size(min = 2, max = 30) String nickname) {}

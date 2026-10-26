package com.pace.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(
        @NotBlank @Size(max = 4096) String appleAuthorizationCode,
        @NotBlank @Size(min = 16, max = 256) String appleNonce) {}

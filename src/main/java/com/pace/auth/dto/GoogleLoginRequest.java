package com.pace.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(@NotBlank @Size(max = 16000) String idToken) {}

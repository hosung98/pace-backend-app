package com.pace.crew.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinByCodeRequest(@NotBlank @Size(max = 16) String code) {}

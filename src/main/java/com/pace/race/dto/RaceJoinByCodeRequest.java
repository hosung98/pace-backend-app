package com.pace.race.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RaceJoinByCodeRequest(@NotBlank @Size(max = 16) String code) {}

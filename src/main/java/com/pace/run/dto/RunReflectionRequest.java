package com.pace.run.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RunReflectionRequest(
        @NotNull @Pattern(regexp = "easy|moderate|hard") String effort,
        @NotNull @Size(max = 500) String note) {}

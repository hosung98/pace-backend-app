package com.pace.crew.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CreateCrewRequest(
        @NotBlank @Size(max = 20) String name,
        @NotNull @Size(max = 100) String intro,
        @Valid CrewRegion region
) {}

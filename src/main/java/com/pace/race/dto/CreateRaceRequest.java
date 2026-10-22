package com.pace.race.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record CreateRaceRequest(
        @NotBlank @Size(max = 60) String title,
        @NotNull Integer distanceMeters,
        @NotNull Instant startsAt,
        @NotNull Integer joinDeadlineMinutes,
        @NotNull Integer minParticipants,
        @NotNull Integer maxParticipants,
        @NotBlank String visibility
) {}

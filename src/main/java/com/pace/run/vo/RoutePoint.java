package com.pace.run.vo;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record RoutePoint(
        @NotNull Instant recordedAt,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        Double altitudeMeters,
        @PositiveOrZero Double accuracyMeters,
        @PositiveOrZero Double speedMetersPerSecond) {}

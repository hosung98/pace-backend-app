package com.pace.run.dto;

import com.pace.run.vo.RoutePoint;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public record CreateRunRequest(
        @Schema(description = "기기에서 생성한 기록 ID. 같은 사용자 안에서 유일해야 합니다.", example = "local-1792561466354")
        @NotBlank @Size(max = 64) String clientRunId,
        @NotNull Instant startedAt,
        @NotNull Instant endedAt,
        @Schema(description = "일시정지를 제외한 러닝 시간(초)")
        @NotNull @Min(0) @Max(86400) Integer durationSeconds,
        @Schema(description = "총 거리(m)")
        @NotNull @Min(0) @Max(300000) Integer distanceMeters,
        @Schema(description = "서버가 시간과 거리로 다시 계산하는 평균 페이스(초/km)")
        @PositiveOrZero Integer averagePaceSecondsPerKm,
        @NotNull List<@NotNull @Valid RoutePoint> routePoints) {}

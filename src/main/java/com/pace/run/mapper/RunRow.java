package com.pace.run.mapper;

import com.pace.run.dto.RunSummaryResponse;
import java.time.Instant;

public record RunRow(long internalId, String id, String clientRunId, String title, Instant startedAt,
                     Instant endedAt, int durationSeconds, int distanceMeters,
                     Integer averagePaceSecondsPerKm, int routePointCount) {
    public RunSummaryResponse toResponse() {
        return new RunSummaryResponse(id, clientRunId, title, startedAt, endedAt, durationSeconds,
                distanceMeters, averagePaceSecondsPerKm, routePointCount);
    }
}

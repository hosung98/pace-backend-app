package com.pace.run.dto;

import java.time.Instant;

public record RunSummaryResponse(String id, String clientRunId, String title, Instant startedAt,
                                 Instant endedAt, int durationSeconds, int distanceMeters,
                                 Integer averagePaceSecondsPerKm, int routePointCount) {}

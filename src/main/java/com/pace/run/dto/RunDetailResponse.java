package com.pace.run.dto;

import java.time.Instant;

public record RunDetailResponse(String id, String clientRunId, String title, Instant startedAt,
                                Instant endedAt, int durationSeconds, int distanceMeters,
                                Integer averagePaceSecondsPerKm, Double averageSpeedKmh,
                                int routePointCount, RunRouteResponse route) {}

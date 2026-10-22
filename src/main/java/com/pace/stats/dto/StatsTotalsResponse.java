package com.pace.stats.dto;

public record StatsTotalsResponse(long distanceMeters, long durationSeconds, long runCount,
                                  Integer averagePaceSecondsPerKm, int longestRunMeters) {}

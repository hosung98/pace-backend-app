package com.pace.stats.mapper;

import java.time.LocalDate;

public record DailyStatsRow(LocalDate day, long distanceMeters, long durationSeconds, long runCount, int longestRunMeters) {}

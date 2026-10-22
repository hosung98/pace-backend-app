package com.pace.stats.dto;

import java.time.LocalDate;
import java.util.List;

public record WeekStatsResponse(LocalDate from, LocalDate to, int goalMeters, long distanceMeters,
                                long runCount, List<Long> dailyDistances) {}

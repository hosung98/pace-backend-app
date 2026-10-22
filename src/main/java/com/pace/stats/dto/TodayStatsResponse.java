package com.pace.stats.dto;

import java.time.LocalDate;

public record TodayStatsResponse(LocalDate date, long distanceMeters) {}

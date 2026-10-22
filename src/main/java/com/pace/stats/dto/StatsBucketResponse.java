package com.pace.stats.dto;

import java.time.LocalDate;

public record StatsBucketResponse(String label, LocalDate from, long distanceMeters, long runCount) {}

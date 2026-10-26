package com.pace.progress.dto;

import java.time.Instant;

public record PersonalBestResponse(String id, String title, double value, String unit,
                                   Instant achievedAt, String runId) {}

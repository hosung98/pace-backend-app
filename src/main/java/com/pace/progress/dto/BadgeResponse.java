package com.pace.progress.dto;

import java.time.Instant;

public record BadgeResponse(String id, String title, String description, int target, int progress,
                            Instant earnedAt) {}

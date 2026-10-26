package com.pace.progress.dto;

import java.util.List;

public record UserProgressResponse(double totalDistanceKm, long totalRuns, double weeklyDistanceKm,
                                   long weeklyRuns, double weeklyGoalKm, List<BadgeResponse> badges,
                                   List<PersonalBestResponse> personalBests, String representativeBadgeId) {}

package com.pace.user.dto;

public record SettingsResponse(int weeklyGoalMeters, String distanceUnit, String timeZone,
                               NotificationSettingsResponse notifications, String profileVisibility) {}

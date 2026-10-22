package com.pace.user.dto;

public record NotificationSettingsResponse(boolean runReminder, boolean weeklySummary, String runReminderTime,
                                            boolean race, boolean crew) {}

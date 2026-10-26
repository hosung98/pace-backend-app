package com.pace.user.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "user_settings")
public class UserSettings {
    @Id
    private Long userId;
    private int weeklyGoalMeters = 20000;
    private String distanceUnit = "km";
    private String timeZone = "Asia/Seoul";
    private boolean notifyRunReminder = true;
    private boolean notifyWeeklySummary = true;
    private LocalTime runReminderTime = LocalTime.of(7, 0);
    private boolean notifyRace = true;
    private boolean notifyCrew = true;
    @Column(length = 40)
    private String representativeBadgeId;
    private Instant updatedAt = Instant.now();

    protected UserSettings() {}
    public UserSettings(long userId) { this.userId = userId; }
    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public int getWeeklyGoalMeters() { return weeklyGoalMeters; }
    public String getDistanceUnit() { return distanceUnit; }
    public String getTimeZone() { return timeZone; }
    public boolean isNotifyRunReminder() { return notifyRunReminder; }
    public boolean isNotifyWeeklySummary() { return notifyWeeklySummary; }
    public LocalTime getRunReminderTime() { return runReminderTime; }
    public boolean isNotifyRace() { return notifyRace; }
    public boolean isNotifyCrew() { return notifyCrew; }
    public String getRepresentativeBadgeId() { return representativeBadgeId; }
    public void setWeeklyGoalMeters(int value) { weeklyGoalMeters = value; }
    public void setDistanceUnit(String value) { distanceUnit = value; }
    public void setTimeZone(String value) { timeZone = value; }
    public void setNotifyRunReminder(boolean value) { notifyRunReminder = value; }
    public void setNotifyWeeklySummary(boolean value) { notifyWeeklySummary = value; }
    public void setRunReminderTime(LocalTime value) { runReminderTime = value; }
    public void setNotifyRace(boolean value) { notifyRace = value; }
    public void setNotifyCrew(boolean value) { notifyCrew = value; }
    public void setRepresentativeBadgeId(String value) { representativeBadgeId = value; }
}

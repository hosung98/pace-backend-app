package com.pace.user.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Pattern;

public class UpdateNotificationRequest {
    @JsonSetter(nulls = Nulls.FAIL)
    private Boolean runReminder;
    @JsonSetter(nulls = Nulls.FAIL)
    private Boolean weeklySummary;
    @JsonSetter(nulls = Nulls.FAIL) @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9]")
    private String runReminderTime;
    @JsonSetter(nulls = Nulls.FAIL)
    private Boolean race;
    @JsonSetter(nulls = Nulls.FAIL)
    private Boolean crew;

    public Boolean runReminder() { return runReminder; }
    public Boolean weeklySummary() { return weeklySummary; }
    public String runReminderTime() { return runReminderTime; }
    public Boolean race() { return race; }
    public Boolean crew() { return crew; }
}

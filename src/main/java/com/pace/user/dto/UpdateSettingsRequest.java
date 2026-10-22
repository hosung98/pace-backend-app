package com.pace.user.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public class UpdateSettingsRequest {
    @JsonSetter(nulls = Nulls.FAIL) @Min(5000) @Max(100000)
    private Integer weeklyGoalMeters;
    @JsonSetter(nulls = Nulls.FAIL) @Pattern(regexp = "km|mi")
    private String distanceUnit;
    @JsonSetter(nulls = Nulls.FAIL) @Size(max = 64)
    private String timeZone;
    @JsonSetter(nulls = Nulls.FAIL) @Valid
    private UpdateNotificationRequest notifications;
    @JsonSetter(nulls = Nulls.FAIL) @Pattern(regexp = "public|private")
    private String profileVisibility;

    public Integer weeklyGoalMeters() { return weeklyGoalMeters; }
    public String distanceUnit() { return distanceUnit; }
    public String timeZone() { return timeZone; }
    public UpdateNotificationRequest notifications() { return notifications; }
    public String profileVisibility() { return profileVisibility; }
}

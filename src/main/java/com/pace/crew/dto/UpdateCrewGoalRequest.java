package com.pace.crew.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateCrewGoalRequest(@Min(1) @Max(10000) int targetKm) {}

package com.pace.crew.dto;

import java.util.List;

public record CrewGoalResponse(String month, int targetKm, double totalKm,
                               boolean canEdit, List<CrewGoalContributorResponse> contributors) {}

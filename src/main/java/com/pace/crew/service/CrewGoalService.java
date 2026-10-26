package com.pace.crew.service;

import com.pace.crew.dto.CrewGoalResponse;

public interface CrewGoalService {
    CrewGoalResponse get(String subject, String crewId);
    CrewGoalResponse update(String subject, String crewId, int targetKm);
    void evaluateCurrentGoal(long userId);
}

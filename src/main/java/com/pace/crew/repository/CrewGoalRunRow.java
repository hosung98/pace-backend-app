package com.pace.crew.repository;

import java.time.Instant;

public interface CrewGoalRunRow {
    long getUserId();
    int getDistanceMeters();
    Instant getEndedAt();
}

package com.pace.stats.dto;

import com.pace.run.mapper.RunRow;
import java.time.Instant;

public record RecentRunResponse(String id, Instant startedAt, int distanceMeters,
                                int durationSeconds, Integer averagePaceSecondsPerKm) {
    public static RecentRunResponse from(RunRow row) {
        return new RecentRunResponse(row.id(), row.startedAt(), row.distanceMeters(), row.durationSeconds(),
                row.averagePaceSecondsPerKm());
    }
}

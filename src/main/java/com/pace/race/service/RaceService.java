package com.pace.race.service;

import com.pace.race.dto.*;
import java.time.Instant;
import java.util.List;

public interface RaceService {
    List<RaceSummaryResponse> list(String subject, String scope, Integer distanceMeters,
                                   Instant from, Instant to, boolean hideFull);
    RaceDetailResponse detail(String subject, String raceId);
    RaceIdResponse create(String subject, CreateRaceRequest request);
    void join(String subject, String raceId);
    void leave(String subject, String raceId);
    RaceIdResponse joinByCode(String subject, String code);
    void cancel(String subject, String raceId);
    void processDeadlines();
}

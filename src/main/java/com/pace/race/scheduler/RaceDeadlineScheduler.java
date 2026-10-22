package com.pace.race.scheduler;

import com.pace.race.service.RaceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RaceDeadlineScheduler {
    private final RaceService races;
    public RaceDeadlineScheduler(RaceService races) { this.races = races; }

    @Scheduled(fixedDelayString = "${pace.race-deadline-delay-ms:15000}")
    public void processDeadlines() {
        races.processDeadlines();
    }
}

package com.pace.race.scheduler;

import com.pace.race.service.RaceService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 대결 모집 마감 시점을 주기적으로 처리하는 스케줄러입니다. */
@Component
public class RaceDeadlineScheduler {
    private final RaceService races;

    public RaceDeadlineScheduler(RaceService races) { this.races = races; }

    /**
     * 공개/비공개 대결의 모집 마감 시각을 지나면 상태를 확정합니다.
     * ShedLock으로 여러 파드 중 하나만 실행합니다.
     */
    @Scheduled(fixedDelayString = "${pace.race-deadline-delay-ms:15000}")
    @SchedulerLock(
            name = "raceDeadlineScheduler.processDeadlines",
            lockAtMostFor = "${pace.scheduler.race-deadline.lock-at-most-for:PT1M}",
            lockAtLeastFor = "${pace.scheduler.race-deadline.lock-at-least-for:PT0S}")
    public void processDeadlines() {
        races.processDeadlines();
    }
}

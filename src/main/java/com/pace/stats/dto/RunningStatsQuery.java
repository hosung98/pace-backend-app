package com.pace.stats.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** 통계 조회 기간·기준일·시간대를 하나의 요청으로 묶습니다. */
public record RunningStatsQuery(String period,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, String timeZone) {
    public RunningStatsQuery {
        period = period == null || period.isEmpty() ? "week" : period;
        timeZone = timeZone == null || timeZone.isEmpty() ? "Asia/Seoul" : timeZone;
    }
}

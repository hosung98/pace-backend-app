package com.pace.crew.dto;

/** 크루 순위와 상세 통계의 집계 기간을 받습니다. */
public record CrewPeriodQuery(String period) {
    public CrewPeriodQuery {
        period = period == null || period.isEmpty() ? "week" : period;
    }
}

package com.pace.stats.dto;

/** 대시보드의 날짜 경계를 결정하는 시간대를 받습니다. */
public record DashboardQuery(String timeZone) {
    public DashboardQuery {
        timeZone = timeZone == null || timeZone.isEmpty() ? "Asia/Seoul" : timeZone;
    }
}

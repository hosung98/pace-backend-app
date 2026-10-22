package com.pace.stats.dto;

public record DashboardResponse(TodayStatsResponse today, WeekStatsResponse week, RecentRunResponse recentRun) {}

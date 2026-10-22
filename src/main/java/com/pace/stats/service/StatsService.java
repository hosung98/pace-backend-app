package com.pace.stats.service;

import com.pace.stats.dto.*;
import java.time.LocalDate;

public interface StatsService {
    DashboardResponse dashboard(String subject, String timeZone);
    RunningStatsResponse running(String subject, String period, LocalDate date, String timeZone);
}

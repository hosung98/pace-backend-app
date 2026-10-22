package com.pace.stats.dto;

import java.time.LocalDate;
import java.util.List;

public record RunningStatsResponse(String period, LocalDate from, LocalDate to, StatsTotalsResponse totals,
                                    List<StatsBucketResponse> buckets, AllTimeStatsResponse allTime) {}

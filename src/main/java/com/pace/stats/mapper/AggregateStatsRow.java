package com.pace.stats.mapper;

public record AggregateStatsRow(long distanceMeters, long durationSeconds, long runCount, int longestRunMeters) {}

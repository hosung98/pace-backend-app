package com.pace.race.dto;

import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;

/** 대결 조회 범위·거리·일시·정원 조건을 묶습니다. */
public record RaceListQuery(String scope, Integer distanceMeters,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to, Boolean hideFull) {
    public RaceListQuery {
        scope = scope == null || scope.isEmpty() ? "open" : scope;
        hideFull = hideFull == null ? false : hideFull;
    }
}

package com.pace.stats.controller;

import com.pace.stats.dto.*;
import com.pace.stats.service.StatsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "대시보드·통계", description = "시간대 기준 주간·월간 집계")
public class StatsController {
    private final StatsService service;
    public StatsController(StatsService service) { this.service = service; }

    @GetMapping("/dashboard")
    @Operation(summary = "오늘 거리·이번 주 목표와 기록·최근 러닝 조회")
    public DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam(defaultValue = "Asia/Seoul") String timeZone) {
        return service.dashboard(jwt.getSubject(), timeZone);
    }

    @GetMapping("/stats/running")
    @Operation(summary = "주간·월간 러닝 통계", description = "주간은 월요일 시작 7칸, 월간은 1~7일 단위 버킷입니다. 페이스는 거리 가중 평균입니다.")
    public RunningStatsResponse running(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(defaultValue = "week") String period,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @RequestParam(defaultValue = "Asia/Seoul") String timeZone) {
        return service.running(jwt.getSubject(), period, date, timeZone);
    }
}

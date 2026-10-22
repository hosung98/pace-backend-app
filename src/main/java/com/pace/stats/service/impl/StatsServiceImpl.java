package com.pace.stats.service.impl;

import com.pace.common.exception.BusinessException;
import com.pace.common.util.TimeZones;
import com.pace.run.util.RouteGeometry;
import com.pace.run.mapper.RunQueryMapper;
import com.pace.user.service.UserAccess;
import com.pace.user.repository.SettingsRepository;
import com.pace.stats.mapper.*;
import com.pace.stats.dto.*;
import com.pace.stats.service.StatsService;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StatsServiceImpl implements StatsService {
    private final StatsQueryMapper queries;
    private final RunQueryMapper runs;
    private final UserAccess access;
    private final SettingsRepository settings;

    public StatsServiceImpl(StatsQueryMapper queries, RunQueryMapper runs, UserAccess access, SettingsRepository settings) {
        this.queries = queries;
        this.runs = runs;
        this.access = access;
        this.settings = settings;
    }

    public DashboardResponse dashboard(String subject, String timeZone) {
        ZoneId zone = TimeZones.require(timeZone);
        long userId = access.id(subject, false);
        LocalDate today = LocalDate.now(zone);
        LocalDate start = monday(today);
        var daily = daily(userId, zone, start, start.plusDays(7));
        var values = start.datesUntil(start.plusDays(7)).map(day -> daily.getOrDefault(day, empty(day))).toList();
        var recent = runs.list(userId, null, null, 1);
        return new DashboardResponse(new TodayStatsResponse(today, daily.getOrDefault(today, empty(today)).distanceMeters()),
                new WeekStatsResponse(start, start.plusDays(6), settings.findById(userId).orElseThrow().getWeeklyGoalMeters(),
                        values.stream().mapToLong(DailyStatsRow::distanceMeters).sum(),
                        values.stream().mapToLong(DailyStatsRow::runCount).sum(),
                        values.stream().map(DailyStatsRow::distanceMeters).toList()),
                recent.isEmpty() ? null : RecentRunResponse.from(recent.getFirst()));
    }

    public RunningStatsResponse running(String subject, String period, LocalDate date, String timeZone) {
        if (!Set.of("week", "month").contains(period)) throw BusinessException.invalid("period는 week 또는 month여야 합니다.");
        ZoneId zone = TimeZones.require(timeZone);
        LocalDate anchor = date == null ? LocalDate.now(zone) : date;
        // 극단적인 연도는 DB 시각 범위를 벗어나므로 요청 단계에서 거부합니다.
        if (anchor.getYear() < 1900 || anchor.getYear() > 9998) throw BusinessException.invalid("날짜 범위가 올바르지 않습니다.");
        LocalDate start = period.equals("week") ? monday(anchor) : anchor.withDayOfMonth(1);
        LocalDate end = period.equals("week") ? start.plusDays(7) : start.plusMonths(1);
        long userId = access.id(subject, false);
        var daily = daily(userId, zone, start, end);
        List<DailyStatsRow> values = start.datesUntil(end).map(day -> daily.getOrDefault(day, empty(day))).toList();
        long distance = values.stream().mapToLong(DailyStatsRow::distanceMeters).sum();
        long duration = values.stream().mapToLong(DailyStatsRow::durationSeconds).sum();
        long count = values.stream().mapToLong(DailyStatsRow::runCount).sum();
        var buckets = new ArrayList<StatsBucketResponse>();
        String[] labels = {"월", "화", "수", "목", "금", "토", "일"};
        if (period.equals("week")) {
            for (int i = 0; i < 7; i++) {
                DailyStatsRow day = values.get(i);
                buckets.add(new StatsBucketResponse(labels[i], day.day(), day.distanceMeters(), day.runCount()));
            }
        } else {
            // 앱과 동일하게 매월 1~7일, 8~14일 단위로 묶습니다.
            for (int offset = 0; offset < values.size(); offset += 7) {
                var week = values.subList(offset, Math.min(offset + 7, values.size()));
                buckets.add(new StatsBucketResponse((offset / 7 + 1) + "주", week.getFirst().day(),
                        week.stream().mapToLong(DailyStatsRow::distanceMeters).sum(),
                        week.stream().mapToLong(DailyStatsRow::runCount).sum()));
            }
        }
        AggregateStatsRow all = queries.allTime(userId);
        return new RunningStatsResponse(period, start, end.minusDays(1),
                new StatsTotalsResponse(distance, duration, count, RouteGeometry.pace(duration, distance),
                        values.stream().mapToInt(DailyStatsRow::longestRunMeters).max().orElse(0)),
                buckets, new AllTimeStatsResponse(all.distanceMeters(), all.runCount(),
                        RouteGeometry.pace(all.durationSeconds(), all.distanceMeters())));
    }

    private Map<LocalDate, DailyStatsRow> daily(long userId, ZoneId zone, LocalDate start, LocalDate end) {
        var result = new HashMap<LocalDate, DailyStatsRow>();
        queries.daily(userId, zone.getId(), start.atStartOfDay(zone).toInstant(), end.atStartOfDay(zone).toInstant())
                .forEach(day -> result.put(day.day(), day));
        return result;
    }

    private LocalDate monday(LocalDate date) { return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
    private DailyStatsRow empty(LocalDate date) { return new DailyStatsRow(date, 0, 0, 0, 0); }
}

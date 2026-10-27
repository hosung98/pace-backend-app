package com.pace.progress.service.impl;

import com.pace.progress.dto.*;
import com.pace.progress.entity.UserBadge;
import com.pace.progress.repository.UserBadgeRepository;
import com.pace.progress.service.ProgressService;
import com.pace.run.entity.Run;
import com.pace.run.repository.RunRepository;
import com.pace.run.repository.RouteRepository;
import com.pace.run.vo.RoutePoint;
import com.pace.progress.util.FastestKilometer;
import com.pace.user.entity.UserSettings;
import com.pace.user.repository.SettingsRepository;
import com.pace.user.repository.UserRepository;
import com.pace.user.service.UserAccess;
import com.pace.crew.service.CrewGoalService;
import com.pace.common.exception.BusinessException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressServiceImpl implements ProgressService {
    private static final double METERS_PER_KM = 1000.0;
    private static final List<BadgeDefinition> CATALOG = List.of(
            new BadgeDefinition("first-run", "First run", "Complete your first run.", 1),
            new BadgeDefinition("single3k", "3K run", "Complete a single run of at least 3 km.", 3000),
            new BadgeDefinition("single5k", "5K run", "Complete a single run of at least 5 km.", 5000),
            new BadgeDefinition("single10k", "10K run", "Complete a single run of at least 10 km.", 10000),
            new BadgeDefinition("total30k", "30 km total", "Run at least 30 km in total.", 30000),
            new BadgeDefinition("total100k", "100 km total", "Run at least 100 km in total.", 100000),
            new BadgeDefinition("total300k", "300 km total", "Run at least 300 km in total.", 300000),
            new BadgeDefinition("weekly3days", "Three-day week", "Run on three distinct days in one week.", 3),
            new BadgeDefinition("monthly10runs", "10 runs in a month", "Complete 10 runs in one calendar month.", 10),
            new BadgeDefinition("crew-goal", "Crew goal", "Contribute to a completed monthly crew goal.", 1)
    );

    private final RunRepository runs;
    private final UserBadgeRepository badges;
    private final SettingsRepository settings;
    private final UserRepository users;
    private final UserAccess access;
    private final CrewGoalService crewGoals;
    private final RouteRepository routes;

    public ProgressServiceImpl(RunRepository runs, UserBadgeRepository badges, SettingsRepository settings,
                               UserRepository users, UserAccess access, CrewGoalService crewGoals,
                               RouteRepository routes) {
        this.runs = runs;
        this.badges = badges;
        this.settings = settings;
        this.users = users;
        this.access = access;
        this.crewGoals = crewGoals;
        this.routes = routes;
    }

    @Override
    @Transactional
    public UserProgressResponse progress(String subject) {
        long userId = access.id(subject, true);
        UserSettings userSettings = settings.findById(userId).orElseThrow();
        ZoneId zone = ZoneId.of(userSettings.getTimeZone());
        List<Run> allRuns = runs.findAllByUserIdAndDeletedAtIsNullOrderByStartedAtAscIdAsc(userId);
        recordEarnedBadges(userId, allRuns, zone);
        crewGoals.evaluateCurrentGoal(userId);

        LocalDate today = LocalDate.now(zone);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant start = weekStart.atStartOfDay(zone).toInstant();
        Instant end = weekStart.plusDays(7).atStartOfDay(zone).toInstant();
        List<Run> thisWeek = allRuns.stream()
                .filter(run -> !run.getStartedAt().isBefore(start) && run.getStartedAt().isBefore(end))
                .toList();
        long totalMeters = allRuns.stream().mapToLong(Run::getDistanceMeters).sum();
        long weeklyMeters = thisWeek.stream().mapToLong(Run::getDistanceMeters).sum();
        List<BadgeResponse> badgeResponses = badgeResponses(userId, allRuns, thisWeek, zone);
        List<PersonalBestResponse> bests = personalBests(allRuns);
        return new UserProgressResponse(totalMeters / METERS_PER_KM, allRuns.size(),
                weeklyMeters / METERS_PER_KM, thisWeek.size(),
                userSettings.getWeeklyGoalMeters() / METERS_PER_KM, badgeResponses, bests,
                userSettings.getRepresentativeBadgeId());
    }

    @Override
    @Transactional
    public UserProgressResponse setRepresentativeBadge(String subject, String badgeId) {
        long userId = access.id(subject, true);
        if (badgeId != null && !badges.existsById(new UserBadge.Key(userId, badgeId))) {
            throw BusinessException.invalid("아직 획득하지 않은 배지는 대표 배지로 설정할 수 없습니다.");
        }
        settings.findById(userId).orElseThrow().setRepresentativeBadgeId(badgeId);
        return progress(subject);
    }

    @Override
    @Transactional
    public void recordAchievements(long userId) {
        users.lockById(userId).orElseThrow();
        String timeZone = settings.findById(userId).orElseThrow().getTimeZone();
        List<Run> allRuns = runs.findAllByUserIdAndDeletedAtIsNullOrderByStartedAtAscIdAsc(userId);
        recordEarnedBadges(userId, allRuns, ZoneId.of(timeZone));
    }

    private void recordEarnedBadges(long userId, List<Run> allRuns, ZoneId zone) {
        Map<String, Instant> achievements = achievements(allRuns, zone);
        Set<String> alreadyEarned = new HashSet<>();
        badges.findAllByUserId(userId).forEach(badge -> alreadyEarned.add(badge.getBadgeId()));
        achievements.forEach((id, earnedAt) -> {
            if (!alreadyEarned.contains(id)) badges.save(new UserBadge(userId, id, earnedAt));
        });
    }

    private Map<String, Instant> achievements(List<Run> allRuns, ZoneId zone) {
        Map<String, Instant> earnedAt = new LinkedHashMap<>();
        long totalMeters = 0;
        Set<LocalDate> daysThisWeek = new HashSet<>();
        LocalDate currentWeekStart = null;
        Map<YearMonth, Integer> monthCounts = new HashMap<>();
        for (Run run : allRuns) {
            Instant startedAt = run.getStartedAt();
            if (earnedAt.isEmpty()) earnedAt.put("first-run", startedAt);

            int distance = run.getDistanceMeters();
            for (int target : List.of(3000, 5000, 10000)) {
                String id = "single" + (target / 1000) + "k";
                if (distance >= target) earnedAt.putIfAbsent(id, startedAt);
            }
            totalMeters += distance;
            for (int target : List.of(30000, 100000, 300000)) {
                String id = "total" + (target / 1000) + "k";
                if (totalMeters >= target) earnedAt.putIfAbsent(id, startedAt);
            }

            LocalDate localDay = startedAt.atZone(zone).toLocalDate();
            LocalDate weekStart = localDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (!weekStart.equals(currentWeekStart)) {
                currentWeekStart = weekStart;
                daysThisWeek.clear();
            }
            if (daysThisWeek.add(localDay) && daysThisWeek.size() >= 3) {
                earnedAt.putIfAbsent("weekly3days", startedAt);
            }
            YearMonth month = YearMonth.from(localDay);
            if (monthCounts.merge(month, 1, Integer::sum) >= 10) {
                earnedAt.putIfAbsent("monthly10runs", startedAt);
            }
        }
        return earnedAt;
    }

    private List<BadgeResponse> badgeResponses(long userId, List<Run> allRuns, List<Run> thisWeek, ZoneId zone) {
        Map<String, UserBadge> earned = new HashMap<>();
        badges.findAllByUserId(userId).forEach(badge -> earned.put(badge.getBadgeId(), badge));
        long totalMeters = allRuns.stream().mapToLong(Run::getDistanceMeters).sum();
        int maxSingleMeters = allRuns.stream().mapToInt(Run::getDistanceMeters).max().orElse(0);
        long distinctWeekDays = thisWeek.stream()
                .map(run -> run.getStartedAt().atZone(zone).toLocalDate()).distinct().count();
        LocalDate today = LocalDate.now(zone);
        YearMonth currentMonth = YearMonth.from(today);
        long monthRuns = allRuns.stream()
                .filter(run -> YearMonth.from(run.getStartedAt().atZone(zone)).equals(currentMonth)).count();
        Map<String, Integer> currentProgress = Map.of(
                "first-run", allRuns.isEmpty() ? 0 : 1,
                "single3k", Math.min(maxSingleMeters, 3000),
                "single5k", Math.min(maxSingleMeters, 5000),
                "single10k", Math.min(maxSingleMeters, 10000),
                "total30k", (int) Math.min(totalMeters, 30000),
                "total100k", (int) Math.min(totalMeters, 100000),
                "total300k", (int) Math.min(totalMeters, 300000),
                "weekly3days", (int) Math.min(distinctWeekDays, 3),
                "monthly10runs", (int) Math.min(monthRuns, 10),
                "crew-goal", earned.containsKey("crew-goal") ? 1 : 0);
        return CATALOG.stream().map(definition -> {
            int progress = currentProgress.get(definition.id());
            return new BadgeResponse(definition.id(), definition.title(), definition.description(),
                    definition.target(), progress,
                    Optional.ofNullable(earned.get(definition.id())).map(UserBadge::getEarnedAt).orElse(null));
        }).toList();
    }

    private List<PersonalBestResponse> personalBests(List<Run> allRuns) {
        List<Run> completed = allRuns.stream().filter(run -> !run.getEndedAt().isAfter(Instant.now()))
                .sorted(Comparator.comparing(Run::getEndedAt).thenComparing(Run::getId)).toList();
        List<PersonalBestResponse> result = new ArrayList<>();
        Run longest = null;
        for (Run run : completed) {
            if (longest == null || run.getDistanceMeters() > longest.getDistanceMeters()) longest = run;
        }
        if (longest != null) {
            result.add(new PersonalBestResponse("longest-run", "가장 긴 러닝",
                    longest.getDistanceMeters() / METERS_PER_KM, "km", longest.getEndedAt(), longest.getPublicId()));
        }
        Map<Long, Run> byId = new HashMap<>();
        completed.forEach(run -> byId.put(run.getId(), run));
        Map<Long, Double> candidates = new HashMap<>();
        routes.findAllById(byId.keySet()).forEach(route -> {
            Run run = byId.get(route.getRunId());
            var points = route.getPoints().stream()
                    .map(point -> point != null && point.recordedAt() != null
                            && !point.recordedAt().isBefore(run.getStartedAt())
                            && !point.recordedAt().isAfter(run.getEndedAt()) ? point
                            : new RoutePoint(null, null, null, null, null, null))
                    .toList();
            FastestKilometer.seconds(points).ifPresent(seconds ->
                    candidates.put(run.getId(), Math.round(seconds * 1000) / 1000.0));
        });
        Run fastest = null;
        double bestSeconds = Double.POSITIVE_INFINITY;
        for (Run run : completed) {
            Double seconds = candidates.get(run.getId());
            if (seconds != null && seconds < bestSeconds) {
                fastest = run;
                bestSeconds = seconds;
            }
        }
        if (fastest != null) {
            result.add(new PersonalBestResponse("fastest-1km", "가장 빠른 1km", bestSeconds,
                    "seconds", fastest.getEndedAt(), fastest.getPublicId()));
        }
        return result;
    }

    private record BadgeDefinition(String id, String title, String description, int target) {}
}

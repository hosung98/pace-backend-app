package com.pace.crew.service.impl;

import com.pace.common.exception.BusinessException;
import com.pace.crew.dto.*;
import com.pace.crew.entity.Crew;
import com.pace.crew.entity.CrewMember;
import com.pace.crew.entity.CrewMonthlyGoal;
import com.pace.crew.repository.*;
import com.pace.crew.service.CrewGoalService;
import com.pace.user.service.UserAccess;
import com.pace.progress.repository.UserBadgeRepository;
import java.time.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrewGoalServiceImpl implements CrewGoalService {
    private static final ZoneId CREW_ZONE = ZoneId.of("Asia/Seoul");
    private final UserAccess access;
    private final CrewRepository crews;
    private final CrewMemberRepository members;
    private final CrewMonthlyGoalRepository goals;
    private final UserBadgeRepository badges;

    public CrewGoalServiceImpl(UserAccess access, CrewRepository crews, CrewMemberRepository members,
                               CrewMonthlyGoalRepository goals, UserBadgeRepository badges) {
        this.access = access;
        this.crews = crews;
        this.members = members;
        this.goals = goals;
        this.badges = badges;
    }

    @Override
    @Transactional
    public void evaluateCurrentGoal(long userId) {
        members.findByUserId(userId).flatMap(member -> crews.findById(member.getCrewId()))
                .flatMap(crew -> crews.lockByPublicId(crew.getPublicId()))
                .filter(crew -> members.existsByCrewIdAndUserId(crew.getId(), userId))
                .ifPresent(crew -> {
                    YearMonth month = YearMonth.now(CREW_ZONE);
                    goals.findByCrewIdAndMonthStart(crew.getId(), month.atDay(1))
                            .ifPresent(goal -> award(crew, goal, month));
                });
    }

    @Override
    @Transactional
    public CrewGoalResponse get(String subject, String publicId) {
        long userId = access.id(subject, false);
        Crew crew = crews.lockByPublicId(publicId).orElseThrow(BusinessException::missing);
        CrewMember member = members.findByCrewIdAndUserId(crew.getId(), userId)
                .orElseThrow(CrewGoalServiceImpl::forbidden);
        YearMonth month = YearMonth.now(CREW_ZONE);
        goals.findByCrewIdAndMonthStart(crew.getId(), month.atDay(1))
                .ifPresent(goal -> award(crew, goal, month));
        return response(crew, member, month);
    }

    @Override
    @Transactional
    public CrewGoalResponse update(String subject, String publicId, int targetKm) {
        long userId = access.id(subject, true);
        Crew crew = crews.lockByPublicId(publicId).orElseThrow(BusinessException::missing);
        CrewMember member = members.findByCrewIdAndUserId(crew.getId(), userId)
                .orElseThrow(CrewGoalServiceImpl::forbidden);
        if (!member.getRole().equals("leader") || !crew.getLeaderUserId().equals(userId)) {
            throw forbidden();
        }
        YearMonth month = YearMonth.now(CREW_ZONE);
        LocalDate monthStart = month.atDay(1);
        CrewMonthlyGoal goal = goals.findByCrewIdAndMonthStart(crew.getId(), monthStart)
                .orElseGet(() -> new CrewMonthlyGoal(crew.getId(), monthStart, targetKm));
        if (!goals.existsById(new CrewMonthlyGoal.Key(crew.getId(), monthStart))
                || goal.getTargetKm() != targetKm) {
            long total = members.monthlyContributors(crew.getId(), userId,
                    monthStart.atStartOfDay(CREW_ZONE).toInstant(),
                    month.plusMonths(1).atDay(1).atStartOfDay(CREW_ZONE).toInstant())
                    .stream().mapToLong(CrewGoalContributorRow::getDistanceMeters).sum();
            goal.configure(targetKm, total < targetKm * 1000L);
        }
        goals.save(goal);
        return response(crew, member, month);
    }

    private void award(Crew crew, CrewMonthlyGoal goal, YearMonth month) {
        if (!goal.isRewardEligible() || goal.getAttainedAt() != null) return;
        List<CrewGoalRunRow> runs = members.rewardRuns(crew.getId(),
                month.atDay(1).atStartOfDay(CREW_ZONE).toInstant(),
                month.plusMonths(1).atDay(1).atStartOfDay(CREW_ZONE).toInstant(),
                goal.getConfiguredAt(), Instant.now());
        long total = 0;
        for (CrewGoalRunRow run : runs) {
            total += run.getDistanceMeters();
            if (total >= goal.getTargetKm() * 1000L) {
                if (run.getEndedAt().isBefore(goal.getConfiguredAt())) return;
                goal.attain(run.getEndedAt());
                runs.stream().filter(contribution -> contribution.getDistanceMeters() > 0
                                && !contribution.getEndedAt().isAfter(run.getEndedAt()))
                        .map(CrewGoalRunRow::getUserId).distinct().sorted()
                        .forEach(userId -> badges.awardCrewGoal(userId, run.getEndedAt()));
                return;
            }
        }
    }

    private CrewGoalResponse response(Crew crew, CrewMember member, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        Instant from = monthStart.atStartOfDay(CREW_ZONE).toInstant();
        Instant until = month.plusMonths(1).atDay(1).atStartOfDay(CREW_ZONE).toInstant();
        List<CrewGoalContributorRow> rows = members.monthlyContributors(crew.getId(), member.getUserId(), from, until);
        long totalMeters = rows.stream().mapToLong(CrewGoalContributorRow::getDistanceMeters).sum();
        List<CrewGoalContributorResponse> contributors = rows.stream()
                .filter(row -> row.getProfileVisibility().equals("public") || row.getIsMe())
                .map(row -> new CrewGoalContributorResponse(row.getUserId(), row.getNickname(),
                        row.getDistanceMeters() / 1000.0))
                .toList();
        int targetKm = goals.findByCrewIdAndMonthStart(crew.getId(), monthStart)
                .map(CrewMonthlyGoal::getTargetKm).orElse(0);
        return new CrewGoalResponse(month.toString(), targetKm, totalMeters / 1000.0,
                member.getRole().equals("leader"), contributors);
    }

    private static BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "크루 멤버만 조회하거나 크루장만 목표를 수정할 수 있습니다.");
    }
}

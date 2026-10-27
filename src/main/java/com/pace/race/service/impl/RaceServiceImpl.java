package com.pace.race.service.impl;

import com.pace.common.exception.BusinessException;
import com.pace.race.dto.CreateRaceRequest;
import com.pace.race.dto.RaceDetailResponse;
import com.pace.race.dto.RaceIdResponse;
import com.pace.race.dto.RaceParticipantResponse;
import com.pace.race.dto.RaceSummaryResponse;
import com.pace.race.entity.Race;
import com.pace.race.entity.RaceParticipant;
import com.pace.race.repository.RaceParticipantRepository;
import com.pace.race.repository.RaceRepository;
import com.pace.race.service.RaceService;
import com.pace.user.entity.User;
import com.pace.user.repository.UserRepository;
import com.pace.user.service.UserAccess;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대결 생성/참가/취소와 모집 마감 확정 로직을 담당합니다.
 * 마감 처리 시점에는 행 잠금으로 동시성 충돌을 제어합니다.
 */
@Service
public class RaceServiceImpl implements RaceService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<Integer> DISTANCES = Set.of(3000, 5000, 10000, 21097);
    private static final Set<Integer> DEADLINES = Set.of(10, 30, 60);
    private final UserAccess access;
    private final UserRepository users;
    private final RaceRepository races;
    private final RaceParticipantRepository participants;

    public RaceServiceImpl(UserAccess access, UserRepository users, RaceRepository races,
                           RaceParticipantRepository participants) {
        this.access = access;
        this.users = users;
        this.races = races;
        this.participants = participants;
    }

    /** 공개 대결 또는 내 대결 목록을 조건에 맞게 조회합니다. */
    @Override
    @Transactional
    public List<RaceSummaryResponse> list(String subject, String scope, Integer distanceMeters,
                                          Instant from, Instant to, boolean hideFull) {
        User viewer = access.user(subject, true);
        if (!"open".equals(scope) && !"mine".equals(scope)) {
            throw BusinessException.invalid("scope는 open 또는 mine이어야 합니다.");
        }
        if (distanceMeters != null && !DISTANCES.contains(distanceMeters)) {
            throw BusinessException.invalid("지원하지 않는 대결 거리입니다.");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw BusinessException.invalid("from은 to보다 앞선 시각이어야 합니다.");
        }
        processDeadlines();
        List<Race> candidates;
        if ("open".equals(scope)) {
            candidates = races.findAllByVisibilityAndCancelledFalseOrderByStartsAtAsc("public");
        } else {
            candidates = participants.findAllByUserId(viewer.getId()).stream()
                    .map(p -> races.findById(p.getRaceId()).orElse(null))
                    .filter(Objects::nonNull).toList();
        }
        Instant now = Instant.now();
        return candidates.stream()
                .filter(race -> distanceMeters == null || race.getDistanceMeters().equals(distanceMeters))
                .filter(race -> from == null || !race.getStartsAt().isBefore(from))
                .filter(race -> to == null || race.getStartsAt().isBefore(to))
                .filter(race -> !"open".equals(scope) || !race.isCancelled())
                .filter(race -> !"open".equals(scope) || race.getVisibility().equals("public"))
                .filter(race -> !"open".equals(scope) || deadline(race).isAfter(now))
                .filter(race -> !hideFull || !"open".equals(scope)
                        || participants.existsByRaceIdAndUserId(race.getId(), viewer.getId())
                        || participantCount(race) < race.getMaxParticipants())
                .sorted("open".equals(scope)
                        ? Comparator.comparing(Race::getStartsAt)
                        : Comparator.comparing(Race::getStartsAt).reversed())
                .map(race -> summary(race, viewer.getId()))
                .toList();
    }

    /** 단건 대결 상세와 참가자 목록을 조회합니다. */
    @Override
    @Transactional
    public RaceDetailResponse detail(String subject, String raceId) {
        User viewer = access.user(subject, true);
        processDeadlines();
        Race race = races.lockByPublicId(raceId).orElseThrow(BusinessException::missing);
        boolean joined = participants.existsByRaceIdAndUserId(race.getId(), viewer.getId());
        boolean host = race.getHostUserId().equals(viewer.getId());
        if (race.getVisibility().equals("private") && !joined && !host) {
            throw BusinessException.missing();
        }
        List<RaceParticipantResponse> people = participants.participants(race.getId(), race.getHostUserId(),
                        viewer.getId()).stream()
                .map(row -> new RaceParticipantResponse(row.getUserId(), row.getNickname(),
                        row.getIsHost(), row.getIsMe(), null))
                .toList();
        RaceSummaryResponse summary = summary(race, viewer.getId());
        return new RaceDetailResponse(summary.id(), summary.title(), summary.hostNickname(),
                summary.distanceMeters(), summary.startsAt(), summary.joinDeadlineMinutes(),
                summary.minParticipants(), summary.maxParticipants(), summary.participantCount(),
                summary.visibility(), summary.isJoined(), summary.isHost(), summary.cancelled(),
                people, host ? race.getInviteCode() : null);
    }

    /** 새 대결을 생성하고 방장을 첫 참가자로 등록합니다. */
    @Override
    @Transactional
    public RaceIdResponse create(String subject, CreateRaceRequest request) {
        User host = access.user(subject, true);
        validateCreate(request);
        String visibility = request.visibility().trim().toLowerCase(Locale.ROOT);
        String inviteCode = visibility.equals("private") ? newInviteCode() : null;
        Race race = races.saveAndFlush(new Race(request.title().trim(), host.getId(), request.distanceMeters(),
                request.startsAt(), request.joinDeadlineMinutes(), request.minParticipants(),
                request.maxParticipants(), visibility, inviteCode));
        participants.saveAndFlush(new RaceParticipant(race.getId(), host.getId()));
        return new RaceIdResponse(race.getPublicId());
    }

    /** 방장이 모집 중인 대결의 정보를 수정합니다. */
    @Override
    @Transactional
    public void update(String subject, String raceId, CreateRaceRequest request) {
        User host = access.user(subject, true);
        Race race = races.lockByPublicId(raceId).orElseThrow(BusinessException::missing);
        if (!race.getHostUserId().equals(host.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "대결 방장만 수정할 수 있습니다.");
        }
        if (race.isCancelled() || race.getDeadlineProcessedAt() != null) {
            throw BusinessException.conflict("모집 중인 대결만 수정할 수 있습니다.");
        }
        ensureBeforeDeadline(race);
        validateCreate(request);
        long count = participants.countForRace(race.getId());
        if (request.maxParticipants() < count) {
            throw BusinessException.conflict("최대 인원은 현재 참가 인원보다 작을 수 없습니다.");
        }
        String visibility = request.visibility().trim().toLowerCase(Locale.ROOT);
        boolean termsChanged = !race.getDistanceMeters().equals(request.distanceMeters())
                || !race.getStartsAt().equals(request.startsAt())
                || !race.getJoinDeadlineMinutes().equals(request.joinDeadlineMinutes())
                || !race.getMinParticipants().equals(request.minParticipants())
                || !race.getVisibility().equals(visibility);
        if (count > 1 && termsChanged) {
            throw BusinessException.conflict("다른 참가자가 있으면 이름과 최대 인원만 수정할 수 있습니다.");
        }
        String inviteCode = visibility.equals("private")
                ? (race.getInviteCode() == null ? newInviteCode() : race.getInviteCode()) : null;
        race.updateInfo(request.title().trim(), request.distanceMeters(), request.startsAt(),
                request.joinDeadlineMinutes(), request.minParticipants(), request.maxParticipants(),
                visibility, inviteCode);
    }

    /** 공개 대결에 일반 참가를 처리합니다. */
    @Override
    @Transactional
    public void join(String subject, String raceId) {
        User user = access.user(subject, true);
        Race race = races.lockByPublicId(raceId).orElseThrow(BusinessException::missing);
        if (participants.existsByRaceIdAndUserId(race.getId(), user.getId())) return;
        if (!race.getVisibility().equals("public")) throw BusinessException.missing();
        ensureJoinable(race);
        participants.save(new RaceParticipant(race.getId(), user.getId()));
    }

    /** 참가자 탈퇴를 처리하며 방장은 leave를 허용하지 않습니다. */
    @Override
    @Transactional
    public void leave(String subject, String raceId) {
        User user = access.user(subject, true);
        Race race = races.lockByPublicId(raceId).orElseThrow(BusinessException::missing);
        if (!participants.existsByRaceIdAndUserId(race.getId(), user.getId())) return;
        if (race.getHostUserId().equals(user.getId())) {
            throw BusinessException.conflict("대결 방장은 참가자에서 나갈 수 없습니다. 대결을 취소해 주세요.");
        }
        ensureBeforeDeadline(race);
        participants.remove(race.getId(), user.getId());
    }

    /** 초대 코드로 비공개 대결 참가를 처리합니다. */
    @Override
    @Transactional
    public RaceIdResponse joinByCode(String subject, String code) {
        User user = access.user(subject, true);
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        Race race = races.findByInviteCode(normalized).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "INVITE_CODE_NOT_FOUND", "초대 코드가 올바르지 않습니다."));
        Race locked = races.lockByPublicId(race.getPublicId()).orElseThrow(BusinessException::missing);
        if (participants.existsByRaceIdAndUserId(locked.getId(), user.getId())) {
            return new RaceIdResponse(locked.getPublicId());
        }
        if (!locked.getVisibility().equals("private")) throw BusinessException.missing();
        ensureJoinable(locked);
        participants.save(new RaceParticipant(locked.getId(), user.getId()));
        return new RaceIdResponse(locked.getPublicId());
    }

    /** 방장이 모집 중 대결을 취소합니다. */
    @Override
    @Transactional
    public void cancel(String subject, String raceId) {
        User user = access.user(subject, true);
        Race race = races.lockByPublicId(raceId).orElseThrow(BusinessException::missing);
        if (!race.getHostUserId().equals(user.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "대결 방장만 취소할 수 있습니다.");
        }
        if (race.isCancelled()) return;
        ensureBeforeDeadline(race);
        race.cancel();
    }

    /** 마감 시각을 지난 대결의 인원 스냅샷/취소 여부를 확정합니다. */
    @Override
    @Transactional
    public void processDeadlines() {
        Instant now = Instant.now();
        for (Race candidate : races.dueForDeadlineProcessing(now)) {
            Race race = races.lockById(candidate.getId()).orElse(null);
            if (race == null || race.getDeadlineProcessedAt() != null || deadline(race).isAfter(now)) continue;
            race.processDeadline(now, Math.toIntExact(participants.countForRace(race.getId())));
        }
    }

    private RaceSummaryResponse summary(Race race, long viewerId) {
        boolean host = race.getHostUserId().equals(viewerId);
        long count = race.getDeadlineParticipantCount() == null
                ? participantCount(race) : race.getDeadlineParticipantCount();
        return new RaceSummaryResponse(race.getPublicId(), race.getTitle(),
                Optional.ofNullable(races.hostNickname(race.getHostUserId())).orElse("러너"),
                race.getDistanceMeters(), race.getStartsAt(), race.getJoinDeadlineMinutes(),
                race.getMinParticipants(), race.getMaxParticipants(), count, race.getVisibility(),
                participants.existsByRaceIdAndUserId(race.getId(), viewerId), host, race.isCancelled());
    }

    private long participantCount(Race race) {
        return race.getDeadlineParticipantCount() == null
                ? participants.countForRace(race.getId()) : race.getDeadlineParticipantCount();
    }

    private void ensureJoinable(Race race) {
        if (race.isCancelled()) throw BusinessException.conflict("취소된 대결에는 참가할 수 없습니다.");
        ensureBeforeDeadline(race);
        if (participantCount(race) >= race.getMaxParticipants()) {
            throw BusinessException.conflict("대결 참가 정원이 가득 찼습니다.");
        }
    }

    private void ensureBeforeDeadline(Race race) {
        Instant now = Instant.now();
        if (!deadline(race).isAfter(now)) {
            if (race.getDeadlineProcessedAt() == null) {
                race.processDeadline(now, Math.toIntExact(participants.countForRace(race.getId())));
            }
            throw BusinessException.conflict("대결 모집 마감 후에는 변경할 수 없습니다.");
        }
    }

    private Instant deadline(Race race) {
        return race.getStartsAt().minus(Duration.ofMinutes(race.getJoinDeadlineMinutes()));
    }

    private void validateCreate(CreateRaceRequest request) {
        String title = request.title() == null ? "" : request.title().trim();
        if (title.isEmpty()) throw BusinessException.invalid("대결 이름을 입력해 주세요.");
        if (!DISTANCES.contains(request.distanceMeters())) throw BusinessException.invalid("지원하지 않는 대결 거리입니다.");
        if (!DEADLINES.contains(request.joinDeadlineMinutes())) throw BusinessException.invalid("마감 시간은 10·30·60분 중 선택해 주세요.");
        if (request.minParticipants() < 2 || request.maxParticipants() > 20
                || request.minParticipants() > request.maxParticipants()) {
            throw BusinessException.invalid("참가 인원은 2~20명이며 최소 인원은 최대 인원보다 클 수 없습니다.");
        }
        if (!Set.of("public", "private").contains(request.visibility().trim().toLowerCase(Locale.ROOT))) {
            throw BusinessException.invalid("공개 범위는 public 또는 private여야 합니다.");
        }
        Instant now = Instant.now();
        Instant startsAt = request.startsAt();
        if (startsAt == null || !startsAt.isAfter(now)
                || !startsAt.minus(Duration.ofMinutes(request.joinDeadlineMinutes())).isAfter(now)) {
            throw BusinessException.invalid("대결 시작 시각은 모집 마감보다 충분히 뒤여야 합니다.");
        }
    }

    private String newInviteCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder value = new StringBuilder(8);
            for (int i = 0; i < 8; i++) value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            if (races.findByInviteCode(value.toString()).isEmpty()) return value.toString();
        }
        throw BusinessException.conflict("초대 코드를 생성하지 못했습니다. 다시 시도해 주세요.");
    }
}

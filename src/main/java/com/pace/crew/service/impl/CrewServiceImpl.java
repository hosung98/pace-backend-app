package com.pace.crew.service.impl;

import com.pace.common.exception.BusinessException;
import com.pace.crew.dto.*;
import com.pace.crew.entity.*;
import com.pace.crew.repository.*;
import com.pace.crew.service.CrewService;
import com.pace.user.entity.User;
import com.pace.user.repository.UserRepository;
import com.pace.user.service.UserAccess;
import java.security.SecureRandom;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrewServiceImpl implements CrewService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserAccess access;
    private final UserRepository users;
    private final CrewRepository crews;
    private final CrewMemberRepository members;
    private final CrewJoinRequestRepository requests;
    private final CommunityUserStateRepository states;

    public CrewServiceImpl(UserAccess access, UserRepository users, CrewRepository crews,
                           CrewMemberRepository members, CrewJoinRequestRepository requests,
                           CommunityUserStateRepository states) {
        this.access = access;
        this.users = users;
        this.crews = crews;
        this.members = members;
        this.requests = requests;
        this.states = states;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CrewRankingResponse> rankings(String subject, String period) {
        long viewerId = access.id(subject, false);
        Instant from = periodStart(period);
        List<CrewListRow> rows = crews.rankings(viewerId, from);
        List<CrewRankingResponse> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            CrewListRow row = rows.get(i);
            result.add(new CrewRankingResponse(row.getId(), row.getName(), region(row.getSido(), row.getSigungu()),
                    i + 1, row.getMemberCount(), row.getDistanceMeters(), row.getIsMine()));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CrewSearchResponse> search(String subject, String query, String sido, String sigungu) {
        long viewerId = access.id(subject, false);
        validateRegion(sido, sigungu);
        Instant from = periodStart("week");
        return crews.search(viewerId, clean(query), clean(sido), clean(sigungu), from).stream()
                .map(row -> new CrewSearchResponse(row.getId(), row.getName(), row.getIntro(),
                        region(row.getSido(), row.getSigungu()), row.getMemberCount(), 50,
                        row.getDistanceMeters(), row.getIsMine()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MyCrewResponse myCrew(String subject) {
        long userId = access.id(subject, false);
        return new MyCrewResponse(members.findByUserId(userId)
                .flatMap(member -> crews.findById(member.getCrewId())).map(Crew::getPublicId).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public CrewDetailResponse detail(String subject, String crewId, String period) {
        User viewer = access.user(subject, false);
        Instant from = periodStart(period);
        Crew crew = crews.findByPublicId(crewId).orElseThrow(BusinessException::missing);
        Optional<CrewMember> mine = members.findByCrewIdAndUserId(crew.getId(), viewer.getId());
        boolean leader = crew.getLeaderUserId().equals(viewer.getId());
        String membership = mine.map(value -> value.getRole().equals("leader") ? "leader" : "member")
                .orElseGet(() -> requests.findByCrewIdAndUserId(crew.getId(), viewer.getId()).isPresent()
                        ? "pending" : "none");
        List<CrewMemberStatResponse> memberStats = crews.memberStats(crew.getId(), viewer.getId(), from).stream()
                .map(row -> new CrewMemberStatResponse(row.getUserId(), row.getNickname(), row.getRole(),
                        row.getDistanceMeters(), row.getRunCount(), row.getIsMe()))
                .toList();
        List<CrewListRow> ranked = crews.rankings(viewer.getId(), from);
        Integer rank = null;
        long distance = 0;
        for (int index = 0; index < ranked.size(); index++) {
            CrewListRow row = ranked.get(index);
            if (row.getId().equals(crewId)) {
                rank = index + 1;
                distance = row.getDistanceMeters();
                break;
            }
        }
        return new CrewDetailResponse(crew.getPublicId(), crew.getName(), crew.getIntro(),
                region(crew.getSido(), crew.getSigungu()),
                Optional.ofNullable(crews.nickname(crew.getLeaderUserId())).orElse("크루장"),
                crews.memberCount(crew.getId()), 50, period, rank, distance, memberStats, membership,
                leader ? crew.getInviteCode() : null, leader ? crews.pendingCount(crew.getId()) : 0);
    }

    @Override
    @Transactional
    public CrewIdResponse create(String subject, CreateCrewRequest request) {
        User user = access.user(subject, true);
        ensureCanJoinOrCreate(user.getId());
        ensureNoMembershipOrRequest(user.getId());
        String name = request.name().trim();
        String intro = request.intro().trim();
        if (name.isEmpty()) throw BusinessException.invalid("크루 이름을 입력해 주세요.");
        CrewRegion region = request.region();
        if (region != null) validateRegion(region.sido(), region.sigungu());
        Crew crew = crews.saveAndFlush(new Crew(name, intro,
                region == null ? null : clean(region.sido()), region == null ? null : clean(region.sigungu()),
                user.getId(), newInviteCode()));
        members.save(new CrewMember(crew.getId(), user.getId(), "leader"));
        return new CrewIdResponse(crew.getPublicId());
    }

    @Override
    @Transactional
    public CrewIdResponse joinByCode(String subject, String code) {
        User user = access.user(subject, true);
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        Crew crew = crews.findByInviteCode(normalized).orElseThrow(() ->
                new BusinessException(org.springframework.http.HttpStatus.NOT_FOUND, "INVITE_CODE_NOT_FOUND",
                        "초대 코드가 올바르지 않습니다."));
        Crew locked = crews.lockByPublicId(crew.getPublicId()).orElseThrow(BusinessException::missing);
        Optional<CrewMember> existing = members.findByUserId(user.getId());
        if (existing.isPresent()) {
            Crew existingCrew = crews.findById(existing.get().getCrewId()).orElseThrow(BusinessException::missing);
            if (existingCrew.getId().equals(locked.getId())) return new CrewIdResponse(locked.getPublicId());
            throw BusinessException.conflict("이미 다른 크루에 가입되어 있습니다.");
        }
        ensureCanJoinOrCreate(user.getId());
        if (requests.findByUserId(user.getId()).isPresent()) {
            throw BusinessException.conflict("대기 중인 크루 가입 신청을 먼저 취소해 주세요.");
        }
        ensureCapacity(locked);
        members.save(new CrewMember(locked.getId(), user.getId(), "member"));
        return new CrewIdResponse(locked.getPublicId());
    }

    @Override
    @Transactional
    public void requestJoin(String subject, String crewId) {
        User user = access.user(subject, true);
        Crew crew = crews.lockByPublicId(crewId).orElseThrow(BusinessException::missing);
        Optional<CrewMember> currentMembership = members.findByUserId(user.getId());
        if (currentMembership.isPresent()) {
            if (currentMembership.get().getCrewId().equals(crew.getId())) return;
            throw BusinessException.conflict("이미 가입한 크루가 있습니다.");
        }
        ensureCanJoinOrCreate(user.getId());
        Optional<CrewJoinRequest> existing = requests.findByUserId(user.getId());
        if (existing.isPresent()) {
            if (existing.get().getCrewId().equals(crew.getId())) return;
            throw BusinessException.conflict("다른 크루의 가입 신청이 대기 중입니다.");
        }
        ensureCapacity(crew);
        requests.save(new CrewJoinRequest(crew.getId(), user.getId()));
    }

    @Override
    @Transactional
    public void cancelMyRequest(String subject) {
        User user = access.user(subject, true);
        requests.findByUserId(user.getId()).ifPresent(request ->
                requests.remove(request.getCrewId(), user.getId()));
    }

    @Override
    @Transactional
    public void leave(String subject, String crewId) {
        User user = access.user(subject, true);
        Crew crew = crews.lockByPublicId(crewId).orElseThrow(BusinessException::missing);
        CrewMember member = members.findByCrewIdAndUserId(crew.getId(), user.getId())
                .orElseThrow(() -> BusinessException.conflict("가입한 크루가 아닙니다."));
        long count = crews.memberCount(crew.getId());
        if (member.getRole().equals("leader") && count > 1) {
            throw BusinessException.conflict("크루장은 다른 멤버에게 크루장을 넘긴 뒤 나갈 수 있습니다.");
        }
        members.remove(crew.getId(), user.getId());
        if (count == 1) crews.delete(crew);
        CommunityUserState state = states.findById(user.getId()).orElseGet(() -> new CommunityUserState(user.getId()));
        state.setLastCrewLeftAt(Instant.now());
        states.save(state);
    }

    @Override
    @Transactional
    public InviteCodeResponse regenerateInviteCode(String subject, String crewId) {
        User user = access.user(subject, true);
        Crew crew = crews.lockByPublicId(crewId).orElseThrow(BusinessException::missing);
        requireLeader(crew, user.getId());
        crew.setInviteCode(newInviteCode());
        return new InviteCodeResponse(crew.getInviteCode());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CrewJoinRequestResponse> joinRequests(String subject, String crewId) {
        User user = access.user(subject, false);
        Crew crew = crews.findByPublicId(crewId).orElseThrow(BusinessException::missing);
        requireLeader(crew, user.getId());
        return requests.findAllByCrewIdOrderByCreatedAtAsc(crew.getId()).stream()
                .map(request -> users.findById(request.getUserId())
                        .map(requester -> new CrewJoinRequestResponse(requester.getPublicId(), requester.getNickname()))
                        .orElse(null))
                .filter(Objects::nonNull).toList();
    }

    @Override
    @Transactional
    public void approve(String subject, String crewId, String userId) {
        User leader = access.user(subject, true);
        User target = users.lockActive(userId).orElseThrow(BusinessException::missing);
        Crew crew = crews.lockByPublicId(crewId).orElseThrow(BusinessException::missing);
        requireLeader(crew, leader.getId());
        if (members.findByUserId(target.getId()).isPresent()) {
            CrewMember current = members.findByUserId(target.getId()).orElseThrow();
            if (current.getCrewId().equals(crew.getId())) {
                requests.remove(crew.getId(), target.getId());
                return;
            }
            throw BusinessException.conflict("이미 다른 크루에 가입되어 있습니다.");
        }
        CrewJoinRequest request = requests.findByCrewIdAndUserId(crew.getId(), target.getId())
                .orElseThrow(() -> BusinessException.missing());
        ensureCanJoinOrCreate(target.getId());
        ensureCapacity(crew);
        members.save(new CrewMember(crew.getId(), target.getId(), "member"));
        requests.delete(request);
    }

    @Override
    @Transactional
    public void reject(String subject, String crewId, String userId) {
        User leader = access.user(subject, true);
        Crew crew = crews.lockByPublicId(crewId).orElseThrow(BusinessException::missing);
        requireLeader(crew, leader.getId());
        users.findByPublicIdAndStatus(userId, "active").ifPresent(target ->
                requests.remove(crew.getId(), target.getId()));
    }

    @Override
    @Transactional
    public void transferLeadership(String subject, String crewId, String userId) {
        User leader = access.user(subject, true);
        Crew crew = crews.findByPublicId(crewId).orElseThrow(BusinessException::missing);
        requireLeader(crew, leader.getId());
        User target = users.lockActive(userId).orElseThrow(BusinessException::missing);
        if (target.getId().equals(leader.getId())) return;
        crew = crews.lockByPublicId(crewId).orElseThrow(BusinessException::missing);
        requireLeader(crew, leader.getId());
        CrewMember current = members.findByCrewIdAndUserId(crew.getId(), leader.getId())
                .orElseThrow(BusinessException::missing);
        CrewMember next = members.findByCrewIdAndUserId(crew.getId(), target.getId())
                .orElseThrow(() -> BusinessException.conflict("크루 멤버에게만 크루장을 넘길 수 있습니다."));
        current.setRole("member");
        next.setRole("leader");
        crew.setLeaderUserId(target.getId());
    }

    private void ensureNoMembershipOrRequest(long userId) {
        if (members.findByUserId(userId).isPresent()) throw BusinessException.conflict("이미 가입한 크루가 있습니다.");
        if (requests.findByUserId(userId).isPresent()) throw BusinessException.conflict("대기 중인 크루 가입 신청이 있습니다.");
    }

    private void ensureCanJoinOrCreate(long userId) {
        Instant leftAt = states.findById(userId).map(CommunityUserState::getLastCrewLeftAt).orElse(null);
        if (leftAt != null && leftAt.isAfter(Instant.now().minus(Duration.ofHours(24)))) {
            throw BusinessException.conflict("크루를 나온 뒤 24시간 동안은 새 크루에 가입하거나 만들 수 없습니다.");
        }
    }

    private void ensureCapacity(Crew crew) {
        if (crews.memberCount(crew.getId()) >= 50) throw BusinessException.conflict("크루 정원이 가득 찼습니다.");
    }

    private void requireLeader(Crew crew, long userId) {
        if (!crew.getLeaderUserId().equals(userId)) {
            throw new BusinessException(org.springframework.http.HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "크루장만 이 작업을 할 수 있습니다.");
        }
    }

    private Instant periodStart(String period) {
        LocalDate today = LocalDate.now(SEOUL);
        LocalDate start;
        if ("week".equals(period)) {
            start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        } else if ("month".equals(period)) {
            start = today.withDayOfMonth(1);
        } else {
            throw BusinessException.invalid("기간은 week 또는 month여야 합니다.");
        }
        return start.atStartOfDay(SEOUL).toInstant();
    }

    private void validateRegion(String sido, String sigungu) {
        if ((sido == null) != (sigungu == null)) {
            throw BusinessException.invalid("시·도와 시·군·구를 함께 입력하거나 모두 생략해 주세요.");
        }
        if (sido != null && (sido.isBlank() || sigungu.isBlank() || sido.length() > 30 || sigungu.length() > 40)) {
            throw BusinessException.invalid("지역 값이 올바르지 않습니다.");
        }
    }

    private CrewRegion region(String sido, String sigungu) {
        return sido == null || sigungu == null ? null : new CrewRegion(sido, sigungu);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String newInviteCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder value = new StringBuilder(8);
            for (int i = 0; i < 8; i++) value.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            if (!crews.existsByInviteCode(value.toString())) return value.toString();
        }
        throw BusinessException.conflict("초대 코드를 생성하지 못했습니다. 다시 시도해 주세요.");
    }
}

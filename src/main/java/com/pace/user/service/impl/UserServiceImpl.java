package com.pace.user.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pace.auth.provider.AppleGrantRevoker;
import com.pace.auth.repository.ProviderRepository;
import com.pace.auth.repository.TokenRepository;
import com.pace.common.exception.BusinessException;
import com.pace.common.util.TimeZones;
import com.pace.run.repository.RunRepository;
import com.pace.user.dto.DeleteAccountRequest;
import com.pace.user.dto.MeResponse;
import com.pace.user.dto.NotificationSettingsResponse;
import com.pace.user.dto.SettingsResponse;
import com.pace.user.dto.UpdateNotificationRequest;
import com.pace.user.dto.UpdateProfileRequest;
import com.pace.user.dto.UpdateSettingsRequest;
import com.pace.user.dto.UserCountsResponse;
import com.pace.user.entity.User;
import com.pace.user.entity.UserSettings;
import com.pace.user.repository.SettingsRepository;
import com.pace.user.repository.UserRepository;
import com.pace.user.service.UserAccess;
import com.pace.user.service.UserService;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 프로필/설정/탈퇴 처리의 트랜잭션 경계를 담당하는 서비스입니다.
 * 민감한 탈퇴 플로우는 Apple 재인증과 토큰 폐기까지 함께 수행합니다.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private static final Logger log = LogManager.getLogger(UserServiceImpl.class);
    private static final String HANDLE_REGEX = "^[a-z0-9_.]{3,30}$";
    private static final int MAX_BIO_LENGTH = 150;
    private static final int WEEKLY_GOAL_STEP = 5000;

    private final UserAccess access;
    private final UserRepository users;
    private final SettingsRepository settings;
    private final TokenRepository tokens;
    private final RunRepository runs;
    private final ProviderRepository providers;
    private final AppleGrantRevoker appleRevoker;

    /** 현재 인증 사용자의 프로필 기본 정보를 조회합니다. */
    @Transactional(readOnly = true)
    public MeResponse me(String subject) {
        log.debug("내 정보 조회 진입: userId={}", subject);
        MeResponse me = response(access.user(subject, false));
        log.debug("내 정보 조회 완료: userId={}", subject);
        return me;
    }

    /** 닉네임/핸들/소개/공개 범위를 부분 수정합니다. */
    @Transactional
    public MeResponse update(String subject, UpdateProfileRequest request) {
        log.debug("프로필 수정 진입: userId={}", subject);
        User user = access.user(subject, true);
        if (request.nickname() != null) {
            if (request.nickname().isBlank()) throw BusinessException.invalid("닉네임은 공백만 사용할 수 없습니다.");
            user.setNickname(request.nickname());
        }
        if (request.handle() != null) {
            String handle = nullableText(request.handle(), "handle");
            if (handle != null && !handle.matches(HANDLE_REGEX)) {
                throw BusinessException.invalid("사용자 ID는 영문 소문자·숫자·밑줄·마침표로 3~30자여야 합니다.");
            }
            user.setHandle(handle);
        }
        if (request.bio() != null) {
            String bio = nullableText(request.bio(), "bio");
            if (bio != null && bio.length() > MAX_BIO_LENGTH) throw BusinessException.invalid("소개는 150자 이하여야 합니다.");
            user.setBio(bio);
        }
        if (request.profileVisibility() != null) user.setProfileVisibility(request.profileVisibility());
        users.flush();
        MeResponse response = response(user);
        log.info("프로필 수정 완료: userId={}", subject);
        return response;
    }

    /** 현재 인증 사용자의 설정 정보를 조회합니다. */
    @Transactional(readOnly = true)
    public SettingsResponse settings(String subject) {
        log.debug("설정 조회 진입: userId={}", subject);
        User user = access.user(subject, false);
        SettingsResponse response = response(settings.findById(user.getId()).orElseThrow(), user);
        log.debug("설정 조회 완료: userId={}", subject);
        return response;
    }

    /** 목표·단위·시간대·알림 설정을 부분 수정합니다. */
    @Transactional
    public SettingsResponse updateSettings(String subject, UpdateSettingsRequest request) {
        log.debug("설정 수정 진입: userId={}", subject);
        User user = access.user(subject, true);
        UserSettings value = settings.findById(user.getId()).orElseThrow();
        if (request.weeklyGoalMeters() != null) {
            if (request.weeklyGoalMeters() % WEEKLY_GOAL_STEP != 0) {
                throw BusinessException.invalid("주간 목표는 5,000m 단위여야 합니다.");
            }
            value.setWeeklyGoalMeters(request.weeklyGoalMeters());
        }
        if (request.distanceUnit() != null) value.setDistanceUnit(request.distanceUnit());
        if (request.timeZone() != null) {
            TimeZones.require(request.timeZone());
            value.setTimeZone(request.timeZone());
        }
        if (request.profileVisibility() != null) user.setProfileVisibility(request.profileVisibility());
        UpdateNotificationRequest notifications = request.notifications();
        if (notifications != null) {
            if (notifications.runReminder() != null) value.setNotifyRunReminder(notifications.runReminder());
            if (notifications.weeklySummary() != null) value.setNotifyWeeklySummary(notifications.weeklySummary());
            if (notifications.race() != null) value.setNotifyRace(notifications.race());
            if (notifications.crew() != null) value.setNotifyCrew(notifications.crew());
            if (notifications.runReminderTime() != null) value.setRunReminderTime(LocalTime.parse(notifications.runReminderTime()));
        }
        SettingsResponse response = response(value, user);
        log.info("설정 수정 완료: userId={}", subject);
        return response;
    }

    /** 계정을 deleting 상태로 전환하고 활성 토큰을 폐기합니다. */
    @Transactional
    public void delete(String subject, DeleteAccountRequest request) {
        log.warn("회원 탈퇴 진입: userId={}", subject);
        User user = access.user(subject, true);
        providers.findAllByUserId(user.getId()).stream().filter(provider -> provider.getProvider().equals("apple"))
                .forEach(provider -> {
                    if (request == null) {
                        throw new BusinessException(HttpStatus.CONFLICT,
                                "APPLE_REAUTH_REQUIRED", "Apple 재인증 코드와 nonce가 있어야 탈퇴할 수 있습니다.");
                    }
                    appleRevoker.revoke(request.appleAuthorizationCode(), request.appleNonce(), provider.getProviderUserId());
        });
        user.markDeleting();
        tokens.revokeUser(user.getId());
        log.warn("회원 탈퇴 처리 완료: userId={}", subject);
    }

    private MeResponse response(User user) {
        return new MeResponse(user.getPublicId(), user.getNickname(), user.getHandle(), user.getProfileImageUrl(),
                user.getBio(), user.getProfileVisibility(),
                providers.findAllByUserId(user.getId()).stream().map(provider -> provider.getProvider()).sorted().toList(),
                user.getCreatedAt(),
                new UserCountsResponse(runs.countByUserIdAndDeletedAtIsNull(user.getId())), null);
    }

    private SettingsResponse response(UserSettings value, User user) {
        return new SettingsResponse(value.getWeeklyGoalMeters(), value.getDistanceUnit(), value.getTimeZone(),
                new NotificationSettingsResponse(value.isNotifyRunReminder(), value.isNotifyWeeklySummary(),
                        value.getRunReminderTime().toString(), value.isNotifyRace(), value.isNotifyCrew()),
                user.getProfileVisibility());
    }

    private String nullableText(JsonNode node, String field) {
        if (node.isNull()) return null;
        if (!node.isTextual()) throw BusinessException.invalid(field + " 값은 문자열 또는 null이어야 합니다.");
        return node.textValue();
    }
}

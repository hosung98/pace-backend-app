package com.pace.user.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pace.auth.repository.TokenRepository;
import com.pace.auth.repository.ProviderRepository;
import com.pace.auth.provider.AppleGrantRevoker;
import com.pace.common.exception.BusinessException;
import com.pace.run.repository.RunRepository;
import com.pace.user.dto.*;
import com.pace.user.entity.*;
import com.pace.user.repository.*;
import com.pace.user.service.*;
import com.pace.common.util.TimeZones;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public MeResponse me(String subject) {
        log.debug("내 정보 조회 진입: userId={}", subject);
        MeResponse me = response(access.user(subject, false));
        log.debug("내 정보 조회 완료: userId={}", subject);
        return me;
    }

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

    @Transactional(readOnly = true)
    public SettingsResponse settings(String subject) {
        log.debug("설정 조회 진입: userId={}", subject);
        User user = access.user(subject, false);
        SettingsResponse response = response(settings.findById(user.getId()).orElseThrow(), user);
        log.debug("설정 조회 완료: userId={}", subject);
        return response;
    }

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
